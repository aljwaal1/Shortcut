# PDF splitting — design

Add a bilingual mobile-first PDF splitter to Shortcut's PDF tools. The user selects a PDF and an output folder, then chooses either numbered single-page PDF files in a `Pages` subfolder or ordered chapter/unit PDFs in a `Chapters` subfolder. Both subfolders live under a new folder derived from the source PDF's name. Never overwrite an existing result folder.

Chapter suggestions come from PDF outline bookmarks first, then recognizable chapter/unit headings in extractable text; boundaries are always editable before export. When detection is unavailable, permit fully manual chapter ranges. Show progress and understandable errors; retain the source PDF untouched. Support SAF folder permissions and Android 8–16.

Approaches considered: export with PdfBox and SAF (chosen, dependencies already installed); platform PdfRenderer (cannot save separate PDFs); external conversion service (unnecessary internet and privacy cost). The chapter detection is advisory and does not silently claim certainty.
