[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$baseUrl = 'http://localhost:8080'
$propertyId = 66L
$expectedOwnerId = 13L
$expectedAreaPrefix = 'SMUPD-'
$originalProperty = $null
$ownerSession = $null
$ownerCsrf = $null
$ownerId = $null
$updateAttempted = $false
$restoreRequired = $false
$overallFailure = $false

function Write-Check {
    param([string]$Result, [string]$Message)
    Write-Host ("{0}: {1}" -f $Result, $Message)
}

function ConvertFrom-SecurePrompt {
    param([System.Security.SecureString]$SecureValue)
    $pointer = [IntPtr]::Zero
    try {
        $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureValue)
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    }
    finally {
        if ($pointer -ne [IntPtr]::Zero) {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
        }
        if ($SecureValue) {
            $SecureValue.Dispose()
        }
    }
}

function Invoke-LocalApi {
    param(
        [Parameter(Mandatory)][string]$Method,
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][Microsoft.PowerShell.Commands.WebRequestSession]$Session,
        [hashtable]$Headers,
        [string]$JsonBody
    )

    $request = @{
        Uri = "$baseUrl$Path"
        Method = $Method
        WebSession = $Session
        UseBasicParsing = $true
        ErrorAction = 'Stop'
    }
    if ($Headers) { $request.Headers = $Headers }
    if ($PSBoundParameters.ContainsKey('JsonBody')) {
        $request.ContentType = 'application/json'
        $request.Body = $JsonBody
    }

    try {
        $response = Invoke-WebRequest @request
        $data = $null
        if (-not [string]::IsNullOrWhiteSpace($response.Content)) {
            try { $data = ConvertFrom-Json -InputObject $response.Content -ErrorAction Stop }
            catch { throw 'The API returned an unreadable JSON response.' }
        }
        return [pscustomobject]@{ Status = [int]$response.StatusCode; Data = $data }
    }
    catch {
        $status = 0
        try { $status = [int]$_.Exception.Response.StatusCode } catch { }
        if ($status -gt 0) {
            return [pscustomobject]@{ Status = $status; Data = $null }
        }
        throw "No HTTP response was received for $Method $Path. Check that the backend is running at localhost:8080."
    }
}

function Get-LocalCsrf {
    param([Microsoft.PowerShell.Commands.WebRequestSession]$Session)
    $result = Invoke-LocalApi -Method 'GET' -Path '/api/v1/auth/csrf' -Session $Session
    Write-Host "GET /api/v1/auth/csrf: HTTP $($result.Status)"
    if ($result.Status -ne 200 -or -not $result.Data.token -or -not $result.Data.headerName) {
        throw "CSRF retrieval failed with HTTP $($result.Status)."
    }
    return $result.Data
}

function New-CsrfHeaders {
    param([Parameter(Mandatory)]$Csrf)
    $headers = @{}
    $headers[[string]$Csrf.headerName] = [string]$Csrf.token
    return $headers
}

function Invoke-LocalLogin {
    param(
        [Parameter(Mandatory)][string]$Email,
        [Parameter(Mandatory)][string]$Password,
        [Parameter(Mandatory)][Microsoft.PowerShell.Commands.WebRequestSession]$Session
    )
    $preLoginCsrf = Get-LocalCsrf -Session $Session
    $headers = New-CsrfHeaders -Csrf $preLoginCsrf
    $loginJson = @{ email = $Email; password = $Password } | ConvertTo-Json -Compress
    try {
        $login = Invoke-LocalApi -Method 'POST' -Path '/api/v1/auth/login' -Session $Session `
            -Headers $headers -JsonBody $loginJson
    }
    finally {
        $loginJson = $null
    }
    if ($login.Status -ne 200) {
        return [pscustomobject]@{ Status = $login.Status; User = $null; Csrf = $null }
    }
    $freshCsrf = Get-LocalCsrf -Session $Session
    return [pscustomobject]@{ Status = $login.Status; User = $login.Data; Csrf = $freshCsrf }
}

function Get-OwnerListing {
    param([Microsoft.PowerShell.Commands.WebRequestSession]$Session)
    $result = Invoke-LocalApi -Method 'GET' -Path '/api/v1/owner/properties' -Session $Session
    Write-Host "GET /api/v1/owner/properties: HTTP $($result.Status)"
    if ($result.Status -ne 200) { throw "Owner listing request failed with HTTP $($result.Status)." }
    $matches = @($result.Data | Where-Object { [long]$_.id -eq $propertyId })
    if ($matches.Count -ne 1) { throw 'The authenticated owner list did not contain exactly one target listing.' }
    return $matches[0]
}

function New-UpdatePayload {
    param($Property, [string]$Title, [int]$Rent, [string[]]$Amenities)
    return @{
        title = $Title
        type = $Property.type
        bhk = $Property.bhk
        sharingType = $Property.sharingType
        area = $Property.area
        city = $Property.city
        addressLine = $Property.addressLine
        latitude = $Property.latitude
        longitude = $Property.longitude
        monthlyRent = $Rent
        deposit = $Property.deposit
        genderPreference = $Property.genderPreference
        description = $Property.description
        amenities = @($Amenities)
    }
}

function Invoke-OwnerUpdate {
    param(
        [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
        [string]$Title,
        [int]$Rent,
        [string[]]$Amenities
    )
    $csrf = Get-LocalCsrf -Session $Session
    $payload = New-UpdatePayload -Property $originalProperty -Title $Title -Rent $Rent -Amenities $Amenities
    $json = $payload | ConvertTo-Json -Depth 5 -Compress
    try {
        $headers = New-CsrfHeaders -Csrf $csrf
        return Invoke-LocalApi -Method 'PUT' -Path "/api/v1/owner/properties/$propertyId" `
            -Session $Session -Headers $headers -JsonBody $json
    }
    finally {
        $json = $null
        $payload = $null
    }
}

function Assert-ListingValues {
    param($Listing, [string]$Title, [int]$Rent, [string[]]$Amenities, [string]$Stage)
    $actualAmenities = @($Listing.amenities | Sort-Object)
    $expectedAmenities = @($Amenities | Sort-Object)
    $amenitiesMatch = (($actualAmenities -join ',') -ceq ($expectedAmenities -join ','))
    if ($Listing.title -ceq $Title -and [int]$Listing.monthlyRent -eq $Rent -and $amenitiesMatch) {
        Write-Check 'PASS' "$Stage persisted on a fresh owner-list request (title, rent, amenities)."
        return $true
    }
    Write-Check 'FAIL' "$Stage did not match the expected title, rent, and amenities after reloading."
    return $false
}

try {
    if (-not (Test-NetConnection -ComputerName 'localhost' -Port 8080 -InformationLevel Quiet)) {
        throw 'Backend port 8080 is not listening. Start it manually with .\run-local.ps1 in a separate PowerShell window.'
    }

    $email = Read-Host 'Disposable owner 13 email'
    if ([string]::IsNullOrWhiteSpace($email)) { throw 'An owner email is required.' }
    $securePassword = Read-Host 'Disposable owner 13 password (hidden)' -AsSecureString
    $password = ConvertFrom-SecurePrompt -SecureValue $securePassword

    $ownerSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $loginResult = Invoke-LocalLogin -Email $email -Password $password -Session $ownerSession
    $password = $null
    if ($loginResult.Status -ne 200) {
        $overallFailure = $true
        throw "POST /api/v1/auth/login returned HTTP $($loginResult.Status); no property update was attempted."
    }
    $ownerId = [long]$loginResult.User.id
    $ownerCsrf = $loginResult.Csrf
    if ($ownerId -ne $expectedOwnerId -or [string]$loginResult.User.role -cne 'OWNER') {
        $overallFailure = $true
        throw 'Login did not resolve to the expected disposable OWNER account (ID 13); no property update was attempted.'
    }
    Write-Check 'PASS' "POST /api/v1/auth/login returned HTTP 200 for the expected disposable OWNER (ID $ownerId)."

    $originalProperty = Get-OwnerListing -Session $ownerSession
    if ($originalProperty.active -ne $true -or $originalProperty.type -cne 'ROOM' -or
        -not ([string]$originalProperty.area).StartsWith($expectedAreaPrefix, [StringComparison]::Ordinal)) {
        throw 'Property 66 failed the expected active ROOM / SMUPD area safety checks; no update was attempted.'
    }
    $originalAmenities = @($originalProperty.amenities | ForEach-Object { [string]$_ })
    $originalTitle = [string]$originalProperty.title
    $originalRent = [int]$originalProperty.monthlyRent
    Write-Check 'PASS' "GET /api/v1/owner/properties returned HTTP 200; property 66 is active and belongs to owner 13."

    $secondEmail = Read-Host 'Optional second disposable OWNER email for cross-owner check (Enter to skip)'
    if (-not [string]::IsNullOrWhiteSpace($secondEmail)) {
        $secondSecurePassword = Read-Host 'Second disposable OWNER password (hidden)' -AsSecureString
        $secondPassword = ConvertFrom-SecurePrompt -SecureValue $secondSecurePassword
        $secondSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
        try {
            $secondLogin = Invoke-LocalLogin -Email $secondEmail -Password $secondPassword -Session $secondSession
            if ($secondLogin.Status -ne 200) {
                Write-Check 'FAIL' "Second-account POST /api/v1/auth/login returned HTTP $($secondLogin.Status); cross-owner check was not attempted."
                $overallFailure = $true
            }
            elseif ([long]$secondLogin.User.id -eq $expectedOwnerId -or
                    [long]$secondLogin.User.id -eq 1L -or
                    [string]$secondLogin.User.role -cne 'OWNER') {
                Write-Check 'SKIP' 'Second account was not a different disposable OWNER; cross-owner check was not attempted.'
            }
            else {
                $crossProbeTitle = 'CROSS-OWNER ACCESS PROBE ' + (Get-Date -Format 'yyyyMMdd-HHmmss')
                $crossPayload = New-UpdatePayload -Property $originalProperty -Title $crossProbeTitle `
                    -Rent ($originalRent + 2000) -Amenities $originalAmenities
                $crossJson = $crossPayload | ConvertTo-Json -Depth 5 -Compress
                try {
                    $crossCsrf = Get-LocalCsrf -Session $secondSession
                    $crossHeaders = New-CsrfHeaders -Csrf $crossCsrf
                    $restoreRequired = $true
                    $crossResult = Invoke-LocalApi -Method 'PUT' -Path "/api/v1/owner/properties/$propertyId" `
                        -Session $secondSession -Headers $crossHeaders -JsonBody $crossJson
                }
                finally {
                    $crossJson = $null
                    $crossPayload = $null
                }
                if ($crossResult.Status -eq 404) {
                    Write-Check 'PASS' 'A different disposable OWNER received HTTP 404 for PUT /api/v1/owner/properties/66.'
                }
                else {
                    Write-Check 'FAIL' "Cross-owner PUT /api/v1/owner/properties/66 returned HTTP $($crossResult.Status), expected 404."
                    $overallFailure = $true
                }
                $originalProperty = Get-OwnerListing -Session $ownerSession
                if ($originalProperty.title -cne $originalTitle -or [int]$originalProperty.monthlyRent -ne $originalRent -or
                    ((@($originalProperty.amenities | Sort-Object) -join ',') -cne (@($originalAmenities | Sort-Object) -join ','))) {
                    throw 'The first owner listing changed during the cross-owner probe; cleanup will restore its original values.'
                }
                $restoreRequired = $false
                Write-Check 'PASS' 'Fresh owner-list read confirmed the cross-owner probe did not change property 66.'
            }
        }
        finally {
            $secondPassword = $null
            if ($secondSession -and $secondLogin -and $secondLogin.Status -eq 200) {
                try {
                    $logoutCsrf = Get-LocalCsrf -Session $secondSession
                    $null = Invoke-LocalApi -Method 'POST' -Path '/api/v1/auth/logout' -Session $secondSession `
                        -Headers (New-CsrfHeaders -Csrf $logoutCsrf)
                }
                catch { Write-Check 'WARN' 'Second disposable session logout could not be confirmed.' }
            }
        }
    }
    else {
        Write-Check 'SKIP' 'Cross-owner protection was not tested; no second disposable account was supplied.'
    }

    $replacementAmenities = if ($originalAmenities -contains 'AC') { @('PARKING') } else { @('AC') }
    $temporaryTitle = 'TEMP OWNER UPDATE VERIFICATION ' + (Get-Date -Format 'yyyyMMdd-HHmmss')
    $temporaryRent = $originalRent + 1000

    $updateAttempted = $true
    $restoreRequired = $true
    $updateResult = Invoke-OwnerUpdate -Session $ownerSession -Title $temporaryTitle `
        -Rent $temporaryRent -Amenities $replacementAmenities
    Write-Host "PUT /api/v1/owner/properties/66: HTTP $($updateResult.Status)"
    if ($updateResult.Status -ne 200) {
        Write-Check 'FAIL' 'Ordinary field and amenity replacement update was rejected; restoration will still be attempted.'
        $overallFailure = $true
    }
    else {
        if ($updateResult.Data.title -ceq $temporaryTitle -and [int]$updateResult.Data.monthlyRent -eq $temporaryRent -and
            ((@($updateResult.Data.amenities | Sort-Object) -join ',') -ceq (@($replacementAmenities | Sort-Object) -join ','))) {
            Write-Check 'PASS' 'PUT response contained the requested temporary title, rent, and replacement amenities.'
        }
        else {
            Write-Check 'FAIL' 'PUT response did not contain the requested update values.'
            $overallFailure = $true
        }
        $freshProperty = Get-OwnerListing -Session $ownerSession
        if (-not (Assert-ListingValues -Listing $freshProperty -Title $temporaryTitle -Rent $temporaryRent `
                    -Amenities $replacementAmenities -Stage 'Ordinary update and amenity replacement')) {
            $overallFailure = $true
        }

        $queryArea = [Uri]::EscapeDataString([string]$originalProperty.area)
        $searchPath = '/api/v1/properties?city=Hyderabad&area=' + $queryArea + '&type=ROOM&maxRent=' + $temporaryRent
        $searchResult = Invoke-LocalApi -Method 'GET' -Path $searchPath `
            -Session (New-Object Microsoft.PowerShell.Commands.WebRequestSession)
        Write-Host "GET /api/v1/properties (city/area/type/maxRent filters): HTTP $($searchResult.Status)"
        $searchHit = @($searchResult.Data | Where-Object { [long]$_.id -eq $propertyId })
        if ($searchResult.Status -eq 200 -and $searchHit.Count -eq 1 -and
            $searchHit[0].title -ceq $temporaryTitle -and [int]$searchHit[0].monthlyRent -eq $temporaryRent) {
            Write-Check 'PASS' 'Public active-property search returned property 66 with its updated title and rent.'
        }
        else {
            Write-Check 'FAIL' 'Public active-property search did not return the expected updated property.'
            $overallFailure = $true
        }

        $matchArea = [Uri]::EscapeDataString([string]$originalProperty.area)
        $matchPath = '/api/v1/properties/matches?city=Hyderabad&area=' + $matchArea +
            '&type=ROOM&maxRent=' + $temporaryRent + '&amenities=' + [Uri]::EscapeDataString(($replacementAmenities -join ','))
        $matchResult = Invoke-LocalApi -Method 'GET' -Path $matchPath `
            -Session (New-Object Microsoft.PowerShell.Commands.WebRequestSession)
        Write-Host "GET /api/v1/properties/matches (area/type/rent/amenity preferences): HTTP $($matchResult.Status)"
        $matchHit = @($matchResult.Data | Where-Object { [long]$_.propertyId -eq $propertyId })
        if ($matchResult.Status -eq 200 -and $matchHit.Count -eq 1 -and
            $matchHit[0].title -ceq $temporaryTitle -and [int]$matchHit[0].monthlyRent -eq $temporaryRent -and
            (@($matchHit[0].amenities | Sort-Object) -join ',') -ceq (@($replacementAmenities | Sort-Object) -join ',')) {
            Write-Check 'PASS' 'Public matching returned property 66 with updated data and replacement amenities.'
        }
        else {
            Write-Check 'FAIL' 'Public matching did not return the expected updated property data.'
            $overallFailure = $true
        }

        $emptyResult = Invoke-OwnerUpdate -Session $ownerSession -Title $temporaryTitle `
            -Rent $temporaryRent -Amenities @()
        Write-Host "PUT /api/v1/owner/properties/66 (amenities=[]): HTTP $($emptyResult.Status)"
        if ($emptyResult.Status -eq 200 -and @($emptyResult.Data.amenities).Count -eq 0) {
            $emptyFresh = Get-OwnerListing -Session $ownerSession
            if (@($emptyFresh.amenities).Count -eq 0) {
                Write-Check 'PASS' 'An explicit empty amenities array cleared associations and persisted on a fresh owner-list request.'
            }
            else {
                Write-Check 'FAIL' 'An explicit empty amenities array did not persist as an empty list.'
                $overallFailure = $true
            }
        }
        else {
            Write-Check 'FAIL' 'An explicit empty amenities array was rejected or returned an unexpected amenity list.'
            $overallFailure = $true
        }
    }
}
catch {
    $overallFailure = $true
    $safeMessage = $_.Exception.Message
    Write-Check 'FAIL' $safeMessage
}
finally {
    $password = $null
    $securePassword = $null
    if ($restoreRequired -and $originalProperty -and $ownerSession -and $ownerId -eq $expectedOwnerId) {
        try {
            $restoreResult = Invoke-OwnerUpdate -Session $ownerSession -Title $originalTitle `
                -Rent $originalRent -Amenities $originalAmenities
            Write-Host "PUT /api/v1/owner/properties/66 (restore original values): HTTP $($restoreResult.Status)"
            if ($restoreResult.Status -eq 200) {
                $restoredProperty = Get-OwnerListing -Session $ownerSession
                $restorationMatches = Assert-ListingValues -Listing $restoredProperty -Title $originalTitle `
                    -Rent $originalRent -Amenities $originalAmenities -Stage 'Restoration'
                if ($restorationMatches -and $restoredProperty.active -eq $true) {
                    Write-Check 'PASS' 'Property 66 remains active; no deactivate or delete request was made.'
                }
                else {
                    Write-Check 'FAIL' 'Restoration verification failed; inspect property 66 before further testing.'
                    $overallFailure = $true
                }
            }
            else {
                Write-Check 'FAIL' "Restoration PUT returned HTTP $($restoreResult.Status); property 66 may require manual restoration."
                $overallFailure = $true
            }
        }
        catch {
            Write-Check 'FAIL' 'Automatic restoration or its fresh-read verification failed; inspect property 66 before further testing.'
            $overallFailure = $true
        }
    }

    if ($ownerSession -and $ownerId -eq $expectedOwnerId) {
        try {
            $logoutCsrf = Get-LocalCsrf -Session $ownerSession
            $logoutResult = Invoke-LocalApi -Method 'POST' -Path '/api/v1/auth/logout' -Session $ownerSession `
                -Headers (New-CsrfHeaders -Csrf $logoutCsrf)
            Write-Host "POST /api/v1/auth/logout: HTTP $($logoutResult.Status)"
            if ($logoutResult.Status -ne 200) { Write-Check 'WARN' 'Owner session logout was not confirmed.' }
        }
        catch { Write-Check 'WARN' 'Owner session logout could not be confirmed.' }
    }
    $ownerCsrf = $null
    $loginResult = $null
}

if ($overallFailure) {
    Write-Check 'RESULT' 'Verification completed with one or more failed or blocked checks.'
    exit 1
}
Write-Check 'RESULT' 'Verification completed. Review the PASS/SKIP lines above.'
