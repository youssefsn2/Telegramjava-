# Telegram Designer Android Java Modern

Android Studio project built in **Java only**. It keeps the same logic as the Python v4 app:

- Load JSON or XLSX product files.
- Detect all fields / columns automatically.
- Let the user write the full Telegram post manually.
- Replace only placeholders like `{Product Desc}` from the loaded file.
- No auto emojis, no auto hashtags, no auto benefits.
- Save templates, settings, bot token, channel ID, selected columns, and posted history.
- Preview the exact caption before sending.
- DRY RUN mode, random order, limit posts, reset posted history.
- Send photo + caption to Telegram through Bot API.

## Design

This version uses a mobile-first modern layout:

- Large blue hero header.
- Bottom navigation.
- Rounded cards.
- Big editor.
- Compact action sections.
- Scrollable screens optimized for phone usage.

## GitHub Actions

The workflow is already included at:

```text
.github/workflows/android-build.yml
```

Upload the project root to GitHub. The build will produce a debug APK artifact named:

```text
TelegramTemplateDesigner-debug-apk
```

## Supported files

- `.json`
- `.xlsx`

For old `.xls`, save/export it as `.xlsx` first.
