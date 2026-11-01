Add-Type -AssemblyName System.Drawing

$installerDir = "C:\Users\pushp\.gemini\antigravity\scratch\Float\installer"
if (!(Test-Path $installerDir)) {
    New-Item -ItemType Directory -Path $installerDir | Out-Null
}

$logoPath = "C:\Users\pushp\.gemini\antigravity\brain\f16c7c94-afc8-4a78-9cc5-1d6598a3057a\.user_uploaded\media_1790062723298.png"
$logo = [System.Drawing.Image]::FromFile($logoPath)

# 1. WizardImageFile: 164 x 314 px
$w = 164
$h = 314
$wizBmp = New-Object System.Drawing.Bitmap($w, $h, [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
$g = [System.Drawing.Graphics]::FromImage($wizBmp)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

# Dark background
$bgBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(24, 24, 27))
$g.FillRectangle($bgBrush, 0, 0, $w, $h)
$bgBrush.Dispose()

# Subtle top gradient
$rectTop = New-Object System.Drawing.Rectangle(0, 0, $w, 180)
$gradBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rectTop, [System.Drawing.Color]::FromArgb(35, 45, 40), [System.Drawing.Color]::FromArgb(24, 24, 27), [System.Drawing.Drawing2D.LinearGradientMode]::Vertical)
$g.FillRectangle($gradBrush, $rectTop)
$gradBrush.Dispose()

# Draw Logo centered at top: 96x96 at x=(164-96)/2=34, y=32
$logoSize = 96
$logoX = [int](($w - $logoSize) / 2)
$logoY = 32
$g.DrawImage($logo, $logoX, $logoY, $logoSize, $logoSize)

# Brand Text: "FLOAT"
$titleFont = New-Object System.Drawing.Font("Segoe UI", 16, [System.Drawing.FontStyle]::Bold)
$titleBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(240, 253, 244))
$sf = New-Object System.Drawing.StringFormat
$sf.Alignment = [System.Drawing.StringAlignment]::Center
$g.DrawString("FLOAT", $titleFont, $titleBrush, ($w / 2), 140, $sf)
$titleFont.Dispose()
$titleBrush.Dispose()

# Subtitle: "Local File Transfer"
$subFont = New-Object System.Drawing.Font("Segoe UI", 9, [System.Drawing.FontStyle]::Regular)
$subBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(110, 231, 183))
$g.DrawString("Local File Transfer", $subFont, $subBrush, ($w / 2), 170, $sf)
$subFont.Dispose()
$subBrush.Dispose()

# Divider line
$pen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(52, 211, 153), 2)
$g.DrawLine($pen, 32, 205, ($w - 32), 205)
$pen.Dispose()

# Footer tag: "by NoishiXzen"
$footFont = New-Object System.Drawing.Font("Segoe UI", 8, [System.Drawing.FontStyle]::Regular)
$footBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(148, 163, 184))
$g.DrawString("by NoishiXzen", $footFont, $footBrush, ($w / 2), 285, $sf)
$footFont.Dispose()
$footBrush.Dispose()

$wizBmp.Save((Join-Path $installerDir "wizard.bmp"), [System.Drawing.Imaging.ImageFormat]::Bmp)
$g.Dispose()
$wizBmp.Dispose()

# 2. WizardSmallImageFile: 55 x 55 px
$sw = 55
$sh = 55
$smallBmp = New-Object System.Drawing.Bitmap($sw, $sh, [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
$sg = [System.Drawing.Graphics]::FromImage($smallBmp)
$sg.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$sg.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$sg.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

$sbgBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(24, 24, 27))
$sg.FillRectangle($sbgBrush, 0, 0, $sw, $sh)
$sbgBrush.Dispose()

# Draw Logo centered: 46x46 at (4, 4)
$sg.DrawImage($logo, 4, 4, 46, 46)

$smallBmp.Save((Join-Path $installerDir "wizard-small.bmp"), [System.Drawing.Imaging.ImageFormat]::Bmp)
$sg.Dispose()
$smallBmp.Dispose()

$logo.Dispose()
Write-Host "Wizard bitmaps created successfully in $installerDir"
