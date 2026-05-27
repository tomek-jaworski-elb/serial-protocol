# PDF Fonts Directory

Place TrueType font files (`.ttf`) here before building.

## Required files (default configuration)

| File                  | Description                     |
|-----------------------|---------------------------------|
| `DejaVuSans.ttf`      | DejaVu Sans — regular variant   |
| `DejaVuSans-Bold.ttf` | DejaVu Sans — bold variant      |

## Download

DejaVu fonts are free and open-source:

```
https://dejavu-fonts.github.io/
```

Direct download (SourceForge):
```
https://sourceforge.net/projects/dejavu/files/dejavu/2.37/dejavu-fonts-ttf-2.37.zip
```

After extracting, copy `DejaVuSans.ttf` and `DejaVuSans-Bold.ttf` into this directory.

## Custom font

To use a different TrueType font, update `application.properties`:

```properties
pdf.report.font.name=MyCustomFont
```

Then place `MyCustomFont.ttf` and `MyCustomFont-Bold.ttf` in this directory.

> **Important:** Both the regular and bold variants must be present.
> The application will fall back to DejaVuSans if the configured font is missing,
> and will refuse to start if neither font can be loaded.
