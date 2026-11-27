param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Fa-f0-9]{40}$')]
    [string]$CertificateThumbprint
)

$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskCertificate = Get-ChildItem Cert:\CurrentUser\Root,Cert:\LocalMachine\Root |
    Where-Object Thumbprint -EQ $CertificateThumbprint | Select-Object -First 1
if (-not $taskCertificate) { throw 'El certificado debe existir ya en el almacén de confianza de Windows.' }
$taskCa = $taskCertificate.Extensions | Where-Object { $_.Oid.Value -eq '2.5.29.19' }
if (-not $taskCa.CertificateAuthority) { throw 'El certificado seleccionado no es una autoridad de certificación.' }
if ($taskCertificate.NotAfter -le (Get-Date)) { throw 'El certificado está caducado.' }

# Solo se exporta el certificado público. No se instala ninguna CA en Android ni se exportan claves.
$taskDirectory = Join-Path $taskRoot '.local/debug-tls'
$taskRaw = Join-Path $taskDirectory 'res/raw'
$taskXml = Join-Path $taskDirectory 'res/xml'
New-Item -ItemType Directory -Path $taskRaw,$taskXml -Force | Out-Null
[IO.File]::WriteAllBytes((Join-Path $taskRaw 'local_development_ca.cer'),
    $taskCertificate.Export([Security.Cryptography.X509Certificates.X509ContentType]::Cert))
@'
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors><certificates src="system" /></trust-anchors>
    </base-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="false">identitytoolkit.googleapis.com</domain>
        <domain includeSubdomains="false">securetoken.googleapis.com</domain>
        <domain includeSubdomains="false">firestore.googleapis.com</domain>
        <domain includeSubdomains="false">www.googleapis.com</domain>
        <trust-anchors>
            <certificates src="system" />
            <certificates src="@raw/local_development_ca" />
        </trust-anchors>
    </domain-config>
</network-security-config>
'@ | Set-Content -LiteralPath (Join-Path $taskXml 'local_development_network_security_config.xml') -Encoding utf8
@'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:usesCleartextTraffic="false"
        android:networkSecurityConfig="@xml/local_development_network_security_config" />
</manifest>
'@ | Set-Content -LiteralPath (Join-Path $taskDirectory 'AndroidManifest.xml') -Encoding utf8

$taskPropertiesPath = Join-Path $taskRoot 'local.properties'
$taskProperties = if (Test-Path -LiteralPath $taskPropertiesPath) { [IO.File]::ReadAllText($taskPropertiesPath) } else { '' }
$taskProperties = [regex]::Replace($taskProperties, '(?m)^debug\.trustedCa=.*\r?\n?', '')
[IO.File]::WriteAllText($taskPropertiesPath, $taskProperties.TrimEnd() + "`r`ndebug.trustedCa=true`r`n", [Text.UTF8Encoding]::new($false))
Write-Output "CA pública configurada únicamente para Debug local y los dominios de Firebase: $($taskCertificate.Subject)"
Write-Output "Huella SHA-1: $($taskCertificate.Thumbprint)"
