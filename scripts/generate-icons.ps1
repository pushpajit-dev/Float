Add-Type -AssemblyName System.Drawing
$srcPath = "C:\Users\pushp\.gemini\antigravity\brain\f16c7c94-afc8-4a78-9cc5-1d6598a3057a\.user_uploaded\media_1790062723298.png"
$pubDir = "C:\Users\pushp\.gemini\antigravity\scratch\Float\public"
$desktopIconsDir = "C:\Users\pushp\.gemini\antigravity\scratch\Float\desktop\src-tauri\icons"

$src = [System.Drawing.Image]::FromFile($srcPath)

function Resize-Img($w, $h, $dest) {
    $bmp = New-Object System.Drawing.Bitmap($w, $h)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.DrawImage($src, 0, 0, $w, $h)
    $bmp.Save($dest, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose()
    $bmp.Dispose()
}

# Public icons
Resize-Img 16 16 (Join-Path $pubDir 'favicon-16.png')
Resize-Img 32 32 (Join-Path $pubDir 'favicon-32.png')
Resize-Img 180 180 (Join-Path $pubDir 'apple-touch-icon.png')
Resize-Img 192 192 (Join-Path $pubDir 'icon-192.png')
Resize-Img 512 512 (Join-Path $pubDir 'icon-512.png')
Resize-Img 512 512 (Join-Path $pubDir 'icon-maskable-512.png')

# Tauri desktop PNG icons
Resize-Img 32 32 (Join-Path $desktopIconsDir '32x32.png')
Resize-Img 64 64 (Join-Path $desktopIconsDir '64x64.png')
Resize-Img 128 128 (Join-Path $desktopIconsDir '128x128.png')
Resize-Img 256 256 (Join-Path $desktopIconsDir '128x128@2x.png')
Resize-Img 512 512 (Join-Path $desktopIconsDir 'icon.png')

$src.Dispose()
Write-Host "Icons generated successfully."
