package com.vr.telegramtemplatedesigner;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

public class MainActivity extends Activity {
    private static final int REQ_PICK_FILE = 101;
    private static final String PREFS = "telegram_template_designer_java";
    private static final int MAX_CAPTION_LEN = 1024;
    private static final Pattern PLACEHOLDER_RE = Pattern.compile("\\{([^{}\\n]+)\\}");

    private final int BG = Color.rgb(246, 248, 252);
    private final int SURFACE = Color.WHITE;
    private final int SURFACE2 = Color.rgb(241, 245, 249);
    private final int ACCENT = Color.rgb(37, 99, 235);
    private final int ACCENT_SOFT = Color.rgb(219, 234, 254);
    private final int TEXT = Color.rgb(17, 24, 39);
    private final int MUTED = Color.rgb(107, 114, 128);
    private final int BORDER = Color.rgb(226, 232, 240);
    private final int SUCCESS = Color.rgb(22, 163, 74);
    private final int ERROR = Color.rgb(220, 38, 38);
    private final int LOG_BG = Color.rgb(17, 24, 39);
    private final int LOG_TEXT = Color.rgb(226, 232, 240);

    private LinearLayout root;
    private LinearLayout navRow;
    private FrameLayout pageHost;
    private TextView statusPill;
    private final List<Button> navButtons = new ArrayList<>();
    private int activePage = 0;

    private LinearLayout pageData;
    private LinearLayout pageWrite;
    private LinearLayout pagePreview;
    private LinearLayout pageSend;

    private EditText filePathText;
    private Spinner imageColumnSpinner;
    private Spinner idColumnSpinner;
    private LinearLayout columnsList;
    private LinearLayout fieldList;
    private EditText templateText;
    private TextView templateStatus;
    private Spinner templateSpinner;
    private EditText exampleBox;
    private EditText previewIndexText;
    private ImageView previewImage;
    private TextView previewImageHint;
    private EditText previewCaption;
    private EditText previewInfo;
    private EditText botTokenText;
    private EditText channelIdText;
    private EditText intervalText;
    private EditText limitText;
    private CheckBox dryRunBox;
    private CheckBox randomOrderBox;
    private CheckBox resetPostedBox;
    private CheckBox removeEmptyLinesBox;
    private EditText logBox;

    private final List<Map<String, String>> rows = new ArrayList<>();
    private final List<String> columns = new ArrayList<>();
    private final LinkedHashMap<String, String> templates = new LinkedHashMap<>();
    private String selectedField = "";
    private String selectedFileName = "";
    private String selectedFileUri = "";
    private volatile boolean stopRequested = false;
    private volatile boolean isPosting = false;
    private ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        loadSettings();
        showPage(0);
    }

    @Override
    protected void onDestroy() {
        stopRequested = true;
        if (executor != null) executor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);

        buildHeader();

        pageHost = new FrameLayout(this);
        LinearLayout.LayoutParams hostLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        hostLp.setMargins(dp(14), dp(12), dp(14), dp(8));
        root.addView(pageHost, hostLp);

        pageData = vertical();
        pageWrite = vertical();
        pagePreview = vertical();
        pageSend = vertical();
        pageHost.addView(pageData, matchFrame());
        pageHost.addView(pageWrite, matchFrame());
        pageHost.addView(pagePreview, matchFrame());
        pageHost.addView(pageSend, matchFrame());

        buildDataPage();
        buildWritePage();
        buildPreviewPage();
        buildSendPage();

        buildTopNav();
    }

    private void buildHeader() {
        LinearLayout hero = vertical();
        hero.setPadding(dp(18), dp(14), dp(18), dp(14));
        hero.setBackground(round(ACCENT, dp(26), 0, 0));
        hero.setElevation(dp(3));
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(132));
        heroLp.setMargins(dp(14), dp(14), dp(14), 0);
        root.addView(hero, heroLp);

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        hero.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView logo = label("✈", 25, ACCENT, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(round(Color.WHITE, dp(18), 0, 0));
        top.addView(logo, new LinearLayout.LayoutParams(dp(56), dp(56)));

        LinearLayout titles = vertical();
        LinearLayout.LayoutParams titlesLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titlesLp.setMargins(dp(14), 0, dp(8), 0);
        top.addView(titles, titlesLp);
        titles.addView(label("Telegram Designer", 23, Color.WHITE, true));
        titles.addView(label("Build posts from your own file fields", 13, Color.rgb(219, 234, 254), false));

        statusPill = label("No file loaded", 11, ACCENT, true);
        statusPill.setGravity(Gravity.CENTER);
        statusPill.setSingleLine(true);
        statusPill.setEllipsize(TextUtils.TruncateAt.END);
        statusPill.setPadding(dp(12), 0, dp(12), 0);
        statusPill.setBackground(round(Color.WHITE, dp(18), 0, 0));
        top.addView(statusPill, new LinearLayout.LayoutParams(dp(150), dp(36)));

        LinearLayout stats = horizontal();
        stats.setGravity(Gravity.CENTER_VERTICAL);
        hero.addView(stats, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
        stats.addView(heroChip("Manual template"), new LinearLayout.LayoutParams(0, dp(30), 1f));
        stats.addView(heroChip("JSON / XLSX"), new LinearLayout.LayoutParams(0, dp(30), 1f));
        stats.addView(heroChip("Telegram send"), new LinearLayout.LayoutParams(0, dp(30), 1f));
    }

    private void buildTopNav() {
        navRow = horizontal();
        navRow.setGravity(Gravity.CENTER);
        navRow.setPadding(dp(8), dp(7), dp(8), dp(7));
        navRow.setBackground(round(SURFACE, dp(26), BORDER, 1));
        navRow.setElevation(dp(5));
        LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(76));
        navLp.setMargins(dp(14), 0, dp(14), dp(14));
        root.addView(navRow, navLp);

        addNavButton(0, "📂\nData");
        addNavButton(1, "✍️\nPost");
        addNavButton(2, "👁\nPreview");
        addNavButton(3, "🚀\nSend");
    }

    private void addNavButton(int index, String text) {
        Button b = button(text, false);
        b.setGravity(Gravity.CENTER);
        b.setAllCaps(false);
        b.setTextSize(11);
        b.setOnClickListener(v -> showPage(index));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        navRow.addView(b, lp);
        navButtons.add(b);
    }

    private void buildDataPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        pageData.addView(scroll, matchLinear());
        LinearLayout content = vertical();
        content.setPadding(0, 0, 0, dp(32));
        scroll.addView(content, matchScroll());
        sectionTitle(content, "📂", "Data", "Load JSON or XLSX, then choose the image and product ID columns.");

        LinearLayout loadCard = card("CHOOSE PRODUCT FILE");
        content.addView(loadCard, fullCardLp());
        LinearLayout fileRow = horizontal();
        fileRow.setGravity(Gravity.CENTER_VERTICAL);
        loadCard.addView(fileRow, matchWrap());
        filePathText = edit("Select .json or .xlsx file", false);
        filePathText.setSingleLine(true);
        filePathText.setFocusable(false);
        fileRow.addView(filePathText, new LinearLayout.LayoutParams(0, dp(50), 1f));
        Button browse = button("Browse", true);
        browse.setOnClickListener(v -> pickFile());
        LinearLayout.LayoutParams browseLp = new LinearLayout.LayoutParams(dp(112), dp(50));
        browseLp.setMargins(dp(10), 0, 0, 0);
        fileRow.addView(browse, browseLp);
        loadCard.addView(smallText("All columns are detected automatically. Nothing is added to your posts unless you use the field in your template."));

        LinearLayout mapCard = card("PHOTO & DUPLICATE CONTROL");
        content.addView(mapCard, fullCardLp());
        mapCard.addView(label("🖼 Image URL column", 12, TEXT, true));
        imageColumnSpinner = spinner(new ArrayList<>());
        mapCard.addView(imageColumnSpinner, matchHeight(dp(50)));
        mapCard.addView(label("🔑 Product ID column", 12, TEXT, true));
        idColumnSpinner = spinner(new ArrayList<>());
        mapCard.addView(idColumnSpinner, matchHeight(dp(50)));
        mapCard.addView(smallText("Product ID prevents posting the same item again. If empty, the app uses the first template value as fallback."));

        LinearLayout columnsCard = card("DETECTED COLUMNS");
        content.addView(columnsCard, fullCardLp());
        columnsList = vertical();
        columnsCard.addView(columnsList, matchWrap());
    }

    private void buildWritePage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        pageWrite.addView(scroll, matchLinear());
        LinearLayout content = vertical();
        content.setPadding(0, 0, 0, dp(32));
        scroll.addView(content, matchScroll());
        sectionTitle(content, "✍️", "Write Post", "Write the post manually. Use {Column Name}. Enter, spaces and blank lines are preserved.");

        LinearLayout editor = card("POST TEMPLATE");
        content.addView(editor, fullCardLp());
        editor.addView(smallText("Example: 🔥 {Product Desc}\n\n💰 Price: {Discount Price}"));
        templateText = edit("Write your Telegram post here...", true);
        templateText.setTextSize(16);
        templateText.setGravity(Gravity.TOP | Gravity.START);
        templateText.setMinLines(9);
        templateText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        templateText.addTextChangedListener(simpleWatcher(this::onTemplateChanged));
        editor.addView(templateText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(260)));
        templateStatus = label("Template is empty. Write your post and use {field}.", 12, MUTED, false);
        templateStatus.setPadding(0, dp(10), 0, dp(6));
        editor.addView(templateStatus, matchWrap());
        LinearLayout actions = horizontal();
        actions.setGravity(Gravity.CENTER_VERTICAL);
        editor.addView(actions, matchWrap());
        Button preview = button("Preview", true);
        preview.setOnClickListener(v -> refreshPreview());
        actions.addView(preview, new LinearLayout.LayoutParams(0, dp(46), 1f));
        Button validate = button("Validate", false);
        validate.setOnClickListener(v -> validateTemplatePopup());
        LinearLayout.LayoutParams midLp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        midLp.setMargins(dp(8), 0, dp(8), 0);
        actions.addView(validate, midLp);
        Button clear = button("Clear", false);
        clear.setOnClickListener(v -> clearTemplate());
        actions.addView(clear, new LinearLayout.LayoutParams(0, dp(46), 1f));
        removeEmptyLinesBox = check("Remove empty lines");
        editor.addView(removeEmptyLinesBox, matchWrap());

        LinearLayout fields = card("AVAILABLE FIELDS");
        content.addView(fields, fullCardLp());
        fields.addView(smallText("Tap a field to select it. Long press a field to insert it directly in the editor."));
        // No nested vertical ScrollView here: the whole page already scrolls.
        // This avoids scroll conflicts when swiping over the fields list.
        fieldList = vertical();
        fields.addView(fieldList, matchWrap());
        LinearLayout fieldActions = horizontal();
        fields.addView(fieldActions, matchWrap());
        Button insert = button("＋ Insert selected field", true);
        insert.setOnClickListener(v -> insertPlaceholder());
        fieldActions.addView(insert, new LinearLayout.LayoutParams(0, dp(44), 1f));
        Button copy = button("Copy name", false);
        copy.setOnClickListener(v -> copyFieldName());
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        copyLp.setMargins(dp(8), 0, 0, 0);
        fieldActions.addView(copy, copyLp);

        LinearLayout templatesCard = card("SAVED TEMPLATES");
        content.addView(templatesCard, fullCardLp());
        templatesCard.addView(smallText("Save different post texts and reuse them later."));
        templateSpinner = spinner(new ArrayList<>());
        templateSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        templatesCard.addView(templateSpinner, matchHeight(dp(50)));
        addTemplateButton(templatesCard, "Load selected", this::loadSelectedTemplate, false);
        addTemplateButton(templatesCard, "Save current", this::saveCurrentTemplate, true);
        addTemplateButton(templatesCard, "Add as new template", this::addNewTemplate, false);
        addTemplateButton(templatesCard, "Rename selected", this::renameTemplate, false);
        addTemplateButton(templatesCard, "Delete selected", this::deleteTemplate, false);

        LinearLayout exampleCard = card("EXAMPLE TEMPLATE");
        content.addView(exampleCard, fullCardLp());
        exampleBox = edit("", true);
        exampleBox.setText("🔥 {Product Desc}\n\n📝 {darija}\n\n💰 الثمن: {Discount Price}\n🔗 {Promotion Url}");
        exampleBox.setTextSize(12);
        exampleBox.setInputType(InputType.TYPE_NULL);
        exampleCard.addView(exampleBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));
        Button useExample = button("Put example in editor", false);
        useExample.setOnClickListener(v -> {
            templateText.setText(exampleBox.getText().toString());
            onTemplateChanged();
        });
        exampleCard.addView(useExample, matchHeight(dp(44)));
    }

    private void buildPreviewPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        pagePreview.addView(scroll, matchLinear());
        LinearLayout content = vertical();
        content.setPadding(0, 0, 0, dp(32));
        scroll.addView(content, matchScroll());
        sectionTitle(content, "👁", "Preview", "Check one product with the exact caption that will be sent to Telegram.");

        LinearLayout controls = card("PRODUCT SELECTOR");
        content.addView(controls, fullCardLp());
        LinearLayout nav = horizontal();
        nav.setGravity(Gravity.CENTER_VERTICAL);
        controls.addView(nav, matchWrap());
        nav.addView(label("Product #", 12, TEXT, true));
        previewIndexText = edit("1", false);
        previewIndexText.setText("1");
        previewIndexText.setInputType(InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams idxLp = new LinearLayout.LayoutParams(dp(78), dp(46));
        idxLp.setMargins(dp(8), 0, dp(8), 0);
        nav.addView(previewIndexText, idxLp);
        Button prev = button("◀", false);
        prev.setOnClickListener(v -> previewPrev());
        nav.addView(prev, new LinearLayout.LayoutParams(dp(56), dp(46)));
        Button next = button("▶", false);
        next.setOnClickListener(v -> previewNext());
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(dp(56), dp(46));
        nlp.setMargins(dp(8), 0, dp(8), 0);
        nav.addView(next, nlp);
        Button refresh = button("Refresh", true);
        refresh.setOnClickListener(v -> refreshPreview());
        nav.addView(refresh, new LinearLayout.LayoutParams(0, dp(46), 1f));

        LinearLayout phone = card("TELEGRAM POST PREVIEW");
        content.addView(phone, fullCardLp());
        TextView channel = label("✈  Your Channel                                      preview", 12, TEXT, true);
        channel.setPadding(dp(12), 0, dp(12), 0);
        channel.setGravity(Gravity.CENTER_VERTICAL);
        channel.setBackground(round(SURFACE2, dp(14), 0, 0));
        phone.addView(channel, matchHeight(dp(46)));
        FrameLayout imgBox = new FrameLayout(this);
        imgBox.setBackground(round(SURFACE2, dp(18), 0, 0));
        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(250));
        imgLp.setMargins(0, dp(10), 0, dp(8));
        phone.addView(imgBox, imgLp);
        previewImage = new ImageView(this);
        previewImage.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        imgBox.addView(previewImage, matchFrame());
        previewImageHint = label("📷 No image loaded", 12, MUTED, false);
        previewImageHint.setGravity(Gravity.CENTER);
        imgBox.addView(previewImageHint, matchFrame());
        phone.addView(label("CAPTION", 11, MUTED, true));
        previewCaption = edit("", true);
        previewCaption.setTextSize(14);
        previewCaption.setInputType(InputType.TYPE_NULL);
        phone.addView(previewCaption, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(260)));

        LinearLayout info = card("DEBUG INFO");
        content.addView(info, fullCardLp());
        previewInfo = edit("", true);
        previewInfo.setTypeface(Typeface.MONOSPACE);
        previewInfo.setInputType(InputType.TYPE_NULL);
        info.addView(previewInfo, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(260)));
    }

    private void buildSendPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        pageSend.addView(scroll, matchLinear());
        LinearLayout content = vertical();
        content.setPadding(0, 0, 0, dp(32));
        scroll.addView(content, matchScroll());
        sectionTitle(content, "🚀", "Send Posts", "Keep DRY RUN enabled until preview and Telegram settings are correct.");

        LinearLayout actions = card("QUICK ACTIONS");
        content.addView(actions, fullCardLp());
        actions.addView(smallText("Preview first, test one post, then start sending."));
        Button preview = button("👁 Preview current product", false);
        preview.setOnClickListener(v -> refreshPreview());
        actions.addView(preview, matchHeight(dp(46)));
        Button test = button("🧪 Test send 1 post", false);
        test.setOnClickListener(v -> testSendOne());
        actions.addView(test, matchHeight(dp(46)));
        Button start = button("▶ Start sending posts", true);
        start.setOnClickListener(v -> startPosting());
        actions.addView(start, matchHeight(dp(48)));
        LinearLayout stopSave = horizontal();
        actions.addView(stopSave, matchWrap());
        Button stop = button("■ Stop", false);
        stop.setOnClickListener(v -> stopPosting());
        stopSave.addView(stop, weightedButton(1f));
        Button save = button("Save settings", false);
        save.setOnClickListener(v -> saveSettings(true));
        stopSave.addView(save, weightedButton(1f));

        LinearLayout credentials = card("BOT CREDENTIALS");
        content.addView(credentials, fullCardLp());
        credentials.addView(smallText("Your token is saved locally in the app settings."));
        credentials.addView(label("BOT_TOKEN", 11, MUTED, true));
        botTokenText = edit("123456:ABCDEF...", false);
        credentials.addView(botTokenText, matchHeight(dp(50)));
        credentials.addView(label("CHANNEL_ID", 11, MUTED, true));
        channelIdText = edit("@channel or -100123456789", false);
        credentials.addView(channelIdText, matchHeight(dp(50)));

        LinearLayout options = card("POSTING OPTIONS");
        content.addView(options, fullCardLp());
        LinearLayout numbers = horizontal();
        options.addView(numbers, matchWrap());
        LinearLayout left = vertical();
        LinearLayout right = vertical();
        numbers.addView(left, weightedCard(1f, 0, 6, 0, 0));
        numbers.addView(right, weightedCard(1f, 6, 0, 0, 0));
        left.addView(label("Interval minutes", 11, MUTED, true));
        intervalText = edit("60", false);
        intervalText.setText("60");
        left.addView(intervalText, matchHeight(dp(50)));
        right.addView(label("Limit posts", 11, MUTED, true));
        limitText = edit("empty = all", false);
        right.addView(limitText, matchHeight(dp(50)));
        dryRunBox = check("DRY RUN: safe test, posts will NOT be sent");
        dryRunBox.setChecked(true);
        randomOrderBox = check("Random order");
        resetPostedBox = check("Reset posted history");
        options.addView(dryRunBox, matchWrap());
        options.addView(randomOrderBox, matchWrap());
        options.addView(resetPostedBox, matchWrap());

        LinearLayout logCard = card("ACTIVITY LOG");
        content.addView(logCard, fullCardLp());
        LinearLayout logTop = horizontal();
        logTop.setGravity(Gravity.CENTER_VERTICAL);
        logCard.addView(logTop, matchWrap());
        logTop.addView(smallText("Logs from preview, test send and auto posting."), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button clearLog = button("Clear log", false);
        clearLog.setOnClickListener(v -> logBox.setText(""));
        logTop.addView(clearLog, new LinearLayout.LayoutParams(dp(110), dp(38)));
        logBox = edit("", true);
        logBox.setTypeface(Typeface.MONOSPACE);
        logBox.setTextColor(LOG_TEXT);
        logBox.setBackground(round(LOG_BG, dp(16), 0, 0));
        logBox.setInputType(InputType.TYPE_NULL);
        logCard.addView(logBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(320)));
    }

    private void addTemplateButton(LinearLayout parent, String text, Runnable action, boolean accent) {
        Button b = button(text, accent);
        b.setOnClickListener(v -> action.run());
        parent.addView(b, matchHeight(dp(42)));
    }

    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/json",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_PICK_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_FILE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) { }
            loadFile(uri);
        }
    }

    private void loadFile(Uri uri) {
        try {
            selectedFileName = getFileName(uri);
            selectedFileUri = uri.toString();
            String lower = selectedFileName.toLowerCase(Locale.ROOT);
            byte[] bytes = readAllBytes(getContentResolver().openInputStream(uri));
            List<Map<String, String>> loadedRows;
            if (lower.endsWith(".json")) {
                loadedRows = loadJsonRows(new String(bytes, StandardCharsets.UTF_8));
            } else if (lower.endsWith(".xlsx")) {
                loadedRows = loadXlsxRows(bytes);
            } else {
                throw new Exception("File must be .json or .xlsx. Old .xls is not supported on Android Java version.");
            }
            if (loadedRows.isEmpty()) throw new Exception("File is empty or contains no valid rows.");

            rows.clear();
            rows.addAll(loadedRows);
            detectColumns();
            filePathText.setText(selectedFileName);
            updateColumnSpinners();
            refreshColumnsViews();
            statusPill.setText("✓ " + rows.size() + " products · " + columns.size() + " columns");
            log("Loaded file: " + selectedFileName);
            saveSettings(false);
            onTemplateChanged();
            toast("Loaded " + rows.size() + " products");
        } catch (Exception e) {
            showError("File error", e.getMessage());
        }
    }

    private List<Map<String, String>> loadJsonRows(String jsonText) throws Exception {
        Object rootObj;
        jsonText = jsonText.trim();
        if (jsonText.startsWith("[")) {
            rootObj = new JSONArray(jsonText);
        } else {
            rootObj = new JSONObject(jsonText);
        }
        JSONArray arr = null;
        if (rootObj instanceof JSONArray) {
            arr = (JSONArray) rootObj;
        } else if (rootObj instanceof JSONObject) {
            JSONObject obj = (JSONObject) rootObj;
            String[] keys = {"posts", "products", "items", "data", "rows", "results"};
            for (String key : keys) {
                if (obj.has(key) && obj.get(key) instanceof JSONArray) {
                    arr = obj.getJSONArray(key);
                    break;
                }
            }
        }
        if (arr == null) throw new Exception("JSON must be an array [{...}] or object containing posts/products/items/data/rows/results.");
        List<Map<String, String>> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            if (!(arr.get(i) instanceof JSONObject)) continue;
            JSONObject obj = arr.getJSONObject(i);
            Map<String, String> row = new LinkedHashMap<>();
            JSONArray names = obj.names();
            if (names == null) continue;
            for (int j = 0; j < names.length(); j++) {
                String key = names.getString(j);
                Object val = obj.opt(key);
                row.put(key, cleanValue(val));
            }
            out.add(row);
        }
        return out;
    }

    private List<Map<String, String>> loadXlsxRows(byte[] xlsxBytes) throws Exception {
        List<String> sharedStrings = parseSharedStrings(xlsxBytes);
        String sheetXml = readZipEntryAsString(xlsxBytes, "xl/worksheets/sheet1.xml");
        if (sheetXml == null) throw new Exception("Could not find first worksheet in .xlsx file.");

        Document doc = parseXml(sheetXml);
        NodeList rowNodes = doc.getElementsByTagName("row");
        List<List<String>> table = new ArrayList<>();
        for (int i = 0; i < rowNodes.getLength(); i++) {
            Element rowEl = (Element) rowNodes.item(i);
            NodeList cellNodes = rowEl.getElementsByTagName("c");
            List<String> rowValues = new ArrayList<>();
            for (int j = 0; j < cellNodes.getLength(); j++) {
                Element cell = (Element) cellNodes.item(j);
                String ref = cell.getAttribute("r");
                int colIdx = columnIndexFromRef(ref);
                while (rowValues.size() <= colIdx) rowValues.add("");
                rowValues.set(colIdx, readCellValue(cell, sharedStrings));
            }
            table.add(rowValues);
        }
        if (table.isEmpty()) return new ArrayList<>();
        List<String> headers = table.get(0);
        List<Map<String, String>> out = new ArrayList<>();
        for (int r = 1; r < table.size(); r++) {
            List<String> vals = table.get(r);
            Map<String, String> row = new LinkedHashMap<>();
            boolean hasValue = false;
            for (int c = 0; c < headers.size(); c++) {
                String header = c < headers.size() ? cleanValue(headers.get(c)) : "";
                if (header.isEmpty()) header = "Column " + (c + 1);
                String value = c < vals.size() ? cleanValue(vals.get(c)) : "";
                if (!value.isEmpty()) hasValue = true;
                row.put(header, value);
            }
            if (hasValue) out.add(row);
        }
        return out;
    }

    private List<String> parseSharedStrings(byte[] xlsxBytes) throws Exception {
        String xml = readZipEntryAsString(xlsxBytes, "xl/sharedStrings.xml");
        List<String> list = new ArrayList<>();
        if (xml == null) return list;
        Document doc = parseXml(xml);
        NodeList siNodes = doc.getElementsByTagName("si");
        for (int i = 0; i < siNodes.getLength(); i++) {
            Element si = (Element) siNodes.item(i);
            NodeList tNodes = si.getElementsByTagName("t");
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < tNodes.getLength(); j++) sb.append(tNodes.item(j).getTextContent());
            list.add(sb.toString());
        }
        return list;
    }

    private String readCellValue(Element cell, List<String> sharedStrings) {
        String type = cell.getAttribute("t");
        if ("inlineStr".equals(type)) {
            NodeList tNodes = cell.getElementsByTagName("t");
            return tNodes.getLength() > 0 ? tNodes.item(0).getTextContent() : "";
        }
        NodeList vNodes = cell.getElementsByTagName("v");
        String raw = vNodes.getLength() > 0 ? vNodes.item(0).getTextContent() : "";
        if ("s".equals(type)) {
            try {
                int idx = Integer.parseInt(raw);
                return idx >= 0 && idx < sharedStrings.size() ? sharedStrings.get(idx) : "";
            } catch (Exception ignored) { return ""; }
        }
        return raw;
    }

    private Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private String readZipEntryAsString(byte[] zipBytes, String entryName) throws Exception {
        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes));
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            if (entryName.equals(entry.getName())) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = zis.read(buf)) > 0) baos.write(buf, 0, n);
                zis.close();
                return baos.toString("UTF-8");
            }
        }
        zis.close();
        return null;
    }

    private int columnIndexFromRef(String ref) {
        if (ref == null) return 0;
        int result = 0;
        for (int i = 0; i < ref.length(); i++) {
            char ch = ref.charAt(i);
            if (ch >= 'A' && ch <= 'Z') result = result * 26 + (ch - 'A' + 1);
            else if (ch >= 'a' && ch <= 'z') result = result * 26 + (ch - 'a' + 1);
            else break;
        }
        return Math.max(0, result - 1);
    }

    private void detectColumns() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        int max = Math.min(100, rows.size());
        for (int i = 0; i < max; i++) set.addAll(rows.get(i).keySet());
        columns.clear();
        columns.addAll(set);
        Collections.sort(columns, (a, b) -> a.toLowerCase(Locale.ROOT).compareTo(b.toLowerCase(Locale.ROOT)));
    }

    private void updateColumnSpinners() {
        List<String> values = new ArrayList<>();
        values.add("");
        values.addAll(columns);
        setSpinnerValues(imageColumnSpinner, values);
        setSpinnerValues(idColumnSpinner, values);

        String img = getPrefString("image_column", "");
        if (img.isEmpty() || !values.contains(img)) img = guessColumn("image url", "image", "photo", "picture", "img");
        setSpinnerSelection(imageColumnSpinner, values, img);

        String id = getPrefString("id_column", "");
        if (id.isEmpty() || !values.contains(id)) id = guessColumn("product id", "productid", "item id", "id");
        setSpinnerSelection(idColumnSpinner, values, id);
    }

    private String guessColumn(String... needles) {
        for (String needle : needles) {
            String n = needle.toLowerCase(Locale.ROOT).trim();
            for (String col : columns) {
                String low = col.toLowerCase(Locale.ROOT).trim();
                if (low.equals(n) || low.contains(n)) return col;
            }
        }
        return "";
    }

    private void refreshColumnsViews() {
        columnsList.removeAllViews();
        fieldList.removeAllViews();
        Map<String, String> sample = rows.isEmpty() ? new LinkedHashMap<>() : rows.get(0);
        for (String col : columns) {
            String val = cleanValue(sample.get(col));
            if (val.length() > 80) val = val.substring(0, 77) + "...";
            columnsList.addView(fieldRow(col, val, false));
            fieldList.addView(fieldRow(col, val, true));
        }
    }

    private View fieldRow(String field, String sample, boolean selectable) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackground(round(SURFACE2, dp(10), BORDER, 1));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, 0, 0, dp(6));
        row.setLayoutParams(rowLp);
        TextView f = label(field, 12, TEXT, true);
        TextView s = label(sample, 11, MUTED, false);
        row.addView(f, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.45f));
        row.addView(s, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.55f));
        if (selectable) {
            row.setOnClickListener(v -> {
                selectedField = field;
                toast("Selected: " + field);
            });
            row.setOnLongClickListener(v -> {
                selectedField = field;
                insertPlaceholder();
                return true;
            });
        }
        return row;
    }

    private void insertPlaceholder() {
        if (selectedField == null || selectedField.isEmpty()) {
            toast("Select a field first");
            return;
        }
        int start = Math.max(templateText.getSelectionStart(), 0);
        templateText.getText().insert(start, "{" + selectedField + "}");
        templateText.requestFocus();
        showKeyboard(templateText);
        onTemplateChanged();
    }

    private void copyFieldName() {
        if (selectedField == null || selectedField.isEmpty()) {
            toast("Select a field first");
            return;
        }
        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("field", selectedField));
        templateStatus.setText("Copied: " + selectedField);
    }

    private void onTemplateChanged() {
        String text = getTemplateText();
        List<String> placeholders = extractPlaceholders(text);
        List<String> missing = missingFields(placeholders);
        if (text.trim().isEmpty()) {
            templateStatus.setText("Template is empty. Write your post and use {field} to insert file values.");
            templateStatus.setTextColor(MUTED);
        } else if (!missing.isEmpty()) {
            templateStatus.setText("⚠ Unknown fields: " + TextUtils.join(", ", missing));
            templateStatus.setTextColor(ERROR);
        } else {
            templateStatus.setText("✓ Fields used: " + (placeholders.isEmpty() ? "no placeholders (static text)" : TextUtils.join(", ", placeholders)));
            templateStatus.setTextColor(SUCCESS);
        }
    }

    private String getTemplateText() {
        return templateText == null ? "" : templateText.getText().toString();
    }

    private List<String> extractPlaceholders(String text) {
        List<String> out = new ArrayList<>();
        Matcher m = PLACEHOLDER_RE.matcher(text == null ? "" : text);
        while (m.find()) {
            String name = m.group(1).trim();
            if (!name.isEmpty() && !out.contains(name)) out.add(name);
        }
        return out;
    }

    private List<String> missingFields(List<String> placeholders) {
        List<String> missing = new ArrayList<>();
        for (String p : placeholders) if (!columns.contains(p)) missing.add(p);
        return missing;
    }

    private void validateTemplatePopup() {
        List<String> placeholders = extractPlaceholders(getTemplateText());
        List<String> missing = missingFields(placeholders);
        if (!missing.isEmpty()) {
            showError("Template error", "Unknown fields:\n" + TextUtils.join("\n", missing));
            return;
        }
        if (getTemplateText().trim().isEmpty()) {
            showError("Empty template", "Write your post first.");
            return;
        }
        alert("Template OK", "All fields valid.\nFields used:\n" + (placeholders.isEmpty() ? "No fields" : TextUtils.join("\n", placeholders)));
    }

    private void clearTemplate() {
        new AlertDialog.Builder(this)
                .setTitle("Clear")
                .setMessage("Clear the entire template?")
                .setPositiveButton("Clear", (d, w) -> templateText.setText(""))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void refreshTemplateSpinner() {
        List<String> names = new ArrayList<>(templates.keySet());
        setSpinnerValues(templateSpinner, names);
    }

    private String selectedTemplateName() {
        Object item = templateSpinner.getSelectedItem();
        return item == null ? "" : item.toString();
    }

    private void loadSelectedTemplate() {
        String name = selectedTemplateName();
        if (name.isEmpty() || !templates.containsKey(name)) {
            toast("Select a saved template first");
            return;
        }
        templateText.setText(templates.get(name));
        log("Loaded template: " + name);
    }

    private void saveCurrentTemplate() {
        String text = getTemplateText();
        if (text.trim().isEmpty()) {
            showError("Empty", "Write a template first.");
            return;
        }
        String name = selectedTemplateName();
        if (name.isEmpty()) {
            askTemplateName("", n -> saveTemplateWithName(n, text));
        } else {
            saveTemplateWithName(name, text);
        }
    }

    private void addNewTemplate() {
        String text = getTemplateText();
        if (text.trim().isEmpty()) {
            showError("Empty", "Write a template first.");
            return;
        }
        askTemplateName("", n -> saveTemplateWithName(n, text));
    }

    private void saveTemplateWithName(String name, String text) {
        if (name == null || name.trim().isEmpty()) return;
        templates.put(name.trim(), text);
        refreshTemplateSpinner();
        setSpinnerSelection(templateSpinner, new ArrayList<>(templates.keySet()), name.trim());
        saveSettings(false);
        toast("Template saved: " + name.trim());
    }

    private void renameTemplate() {
        String old = selectedTemplateName();
        if (old.isEmpty() || !templates.containsKey(old)) {
            toast("Select a saved template first");
            return;
        }
        askTemplateName(old, newName -> {
            if (newName == null || newName.trim().isEmpty() || newName.equals(old)) return;
            String text = templates.remove(old);
            templates.put(newName.trim(), text);
            refreshTemplateSpinner();
            setSpinnerSelection(templateSpinner, new ArrayList<>(templates.keySet()), newName.trim());
            saveSettings(false);
        });
    }

    private void deleteTemplate() {
        String name = selectedTemplateName();
        if (name.isEmpty() || !templates.containsKey(name)) {
            toast("Select a saved template first");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Delete")
                .setMessage("Delete template: " + name + "?")
                .setPositiveButton("Delete", (d, w) -> {
                    templates.remove(name);
                    refreshTemplateSpinner();
                    saveSettings(false);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private interface NameCallback { void onName(String name); }
    private void askTemplateName(String initial, NameCallback callback) {
        EditText input = edit("Template name", false);
        input.setText(initial == null ? "" : initial);
        new AlertDialog.Builder(this)
                .setTitle("Template name")
                .setView(input)
                .setPositiveButton("OK", (d, w) -> callback.onName(input.getText().toString().trim()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void refreshPreview() {
        if (rows.isEmpty()) {
            showError("No data", "Load JSON/XLSX first.");
            return;
        }
        try {
            int idx = currentRowIndex();
            previewIndexText.setText(String.valueOf(idx + 1));
            Map<String, String> row = rows.get(idx);
            String caption = buildCaptionForRow(row);
            String imageUrl = getValue(row, selectedSpinnerValue(imageColumnSpinner));
            setPreviewCaption(caption);
            setPreviewInfo(row, caption, imageUrl);
            loadPreviewImage(imageUrl);
            showPage(2);
        } catch (Exception e) {
            showError("Preview error", e.getMessage());
        }
    }

    private int currentRowIndex() {
        int idx = 0;
        try { idx = Integer.parseInt(previewIndexText.getText().toString().trim()) - 1; } catch (Exception ignored) { }
        if (rows.isEmpty()) return 0;
        return Math.max(0, Math.min(idx, rows.size() - 1));
    }

    private void previewPrev() {
        int idx = Math.max(0, currentRowIndex() - 1);
        previewIndexText.setText(String.valueOf(idx + 1));
        refreshPreview();
    }

    private void previewNext() {
        int idx = Math.min(rows.size() - 1, currentRowIndex() + 1);
        previewIndexText.setText(String.valueOf(idx + 1));
        refreshPreview();
    }

    private String buildCaptionForRow(Map<String, String> row) throws Exception {
        String text = getTemplateText();
        if (text.trim().isEmpty()) throw new Exception("Template is empty. Write your post in section 2.");
        List<String> placeholders = extractPlaceholders(text);
        List<String> missing = missingFields(placeholders);
        if (!missing.isEmpty()) throw new Exception("Unknown fields: " + TextUtils.join(", ", missing));
        Matcher m = PLACEHOLDER_RE.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String field = m.group(1).trim();
            m.appendReplacement(sb, Matcher.quoteReplacement(getValue(row, field)));
        }
        m.appendTail(sb);
        String caption = sb.toString();
        if (removeEmptyLinesBox != null && removeEmptyLinesBox.isChecked()) {
            StringBuilder cleaned = new StringBuilder();
            for (String line : caption.split("\\R")) {
                if (!line.trim().isEmpty()) {
                    if (cleaned.length() > 0) cleaned.append('\n');
                    cleaned.append(rstrip(line));
                }
            }
            caption = cleaned.toString().trim();
        }
        if (caption.length() > MAX_CAPTION_LEN) caption = caption.substring(0, MAX_CAPTION_LEN - 1) + "…";
        return caption;
    }

    private void setPreviewCaption(String caption) {
        previewCaption.setText(caption);
    }

    private void setPreviewInfo(Map<String, String> row, String caption, String imageUrl) {
        List<String> placeholders = extractPlaceholders(getTemplateText());
        StringBuilder lines = new StringBuilder();
        lines.append("Product     : ").append(previewIndexText.getText()).append(" / ").append(rows.size()).append('\n');
        lines.append("Image column: ").append(selectedSpinnerValue(imageColumnSpinner)).append('\n');
        lines.append("Image URL   : ").append(imageUrl == null || imageUrl.isEmpty() ? "(empty)" : imageUrl).append('\n');
        lines.append("Caption len : ").append(caption.length()).append(" / 1024\n");
        lines.append("Fields used : ").append(placeholders.isEmpty() ? "(none)" : TextUtils.join(", ", placeholders)).append("\n\n");
        lines.append("Values substituted:\n");
        for (String field : placeholders) {
            String val = getValue(row, field);
            if (val.length() > 160) val = val.substring(0, 157) + "...";
            lines.append("- ").append(field).append(": ").append(val).append('\n');
        }
        previewInfo.setText(lines.toString());
    }

    private void loadPreviewImage(String imageUrl) {
        previewImage.setImageDrawable(null);
        previewImageHint.setText("📷 Loading image...");
        imageUrl = cleanValue(imageUrl);
        if (imageUrl.isEmpty()) {
            previewImageHint.setText("No image URL found. Check Data / Columns → Image URL.");
            return;
        }
        if (!imageUrl.toLowerCase(Locale.ROOT).startsWith("http://") && !imageUrl.toLowerCase(Locale.ROOT).startsWith("https://")) {
            previewImageHint.setText("Preview supports public http/https image URLs on Android.");
            return;
        }
        String finalUrl = imageUrl;
        executor.execute(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(finalUrl).openConnection();
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(20000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
                Bitmap bmp = BitmapFactory.decodeStream(new BufferedInputStream(conn.getInputStream()));
                runOnUiThread(() -> {
                    if (bmp != null) {
                        previewImage.setImageBitmap(bmp);
                        previewImageHint.setText("");
                    } else {
                        previewImageHint.setText("Could not decode image.");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    previewImageHint.setText("Could not load image preview.\n" + e.getMessage());
                    log("Image preview error: " + e.getMessage());
                });
            }
        });
    }

    private void validateReadyToSend() throws Exception {
        if (rows.isEmpty()) throw new Exception("Load JSON/XLSX first.");
        if (selectedSpinnerValue(imageColumnSpinner).isEmpty()) throw new Exception("Select Image URL column in section 1.");
        if (getTemplateText().trim().isEmpty()) throw new Exception("Write your post template in section 2.");
        List<String> missing = missingFields(extractPlaceholders(getTemplateText()));
        if (!missing.isEmpty()) throw new Exception("Template has unknown fields: " + TextUtils.join(", ", missing));
        if (botTokenText.getText().toString().trim().isEmpty()) throw new Exception("BOT_TOKEN is required.");
        if (channelIdText.getText().toString().trim().isEmpty()) throw new Exception("CHANNEL_ID is required.");
    }

    private void testSendOne() {
        try { validateReadyToSend(); } catch (Exception e) { showError("Test error", e.getMessage()); return; }
        executor.execute(() -> {
            try {
                Map<String, String> row = rows.get(currentRowIndex());
                String caption = buildCaptionForRow(row);
                String imageUrl = getValue(row, selectedSpinnerValue(imageColumnSpinner));
                log("──── TEST SEND 1 POST ────");
                log(caption);
                if (dryRunBox.isChecked()) {
                    log("DRY_RUN=true -> not sent.");
                } else {
                    sendPhoto(botTokenText.getText().toString().trim(), channelIdText.getText().toString().trim(), imageUrl, caption);
                    log("✓ Test post sent.");
                }
            } catch (Exception e) {
                log("ERROR: " + e.getMessage());
                runOnUiThread(() -> showError("Telegram error", e.getMessage()));
            }
        });
    }

    private void startPosting() {
        if (isPosting) {
            toast("Auto posting is already running");
            showPage(3);
            return;
        }
        try { validateReadyToSend(); } catch (Exception e) { showError("Start error", e.getMessage()); return; }
        saveSettings(false);
        stopRequested = false;
        isPosting = true;
        showPage(3);
        executor.execute(this::postingWorker);
    }

    private void stopPosting() {
        stopRequested = true;
        log("■ Stop requested.");
    }

    private void postingWorker() {
        try {
            List<PostItem> items = buildPostItems();
            int intervalMin = parseInt(intervalText.getText().toString(), 60);
            log("▶ START | To post: " + items.size() + " | Dry run: " + dryRunBox.isChecked() + " | Interval: " + intervalMin + " min");
            Set<String> posted = getPostedIds();
            for (int pos = 0; pos < items.size(); pos++) {
                if (stopRequested) { log("■ Stopped."); break; }
                PostItem item = items.get(pos);
                String imageUrl = getValue(item.row, selectedSpinnerValue(imageColumnSpinner));
                String caption = buildCaptionForRow(item.row);
                log("────────────────────────────────────────────");
                log("[" + (pos + 1) + "/" + items.size() + "] ProductId: " + item.id);
                log(caption);
                if (imageUrl.isEmpty()) { log("Skipped: image URL empty."); continue; }
                if (caption.isEmpty()) { log("Skipped: caption empty."); continue; }
                if (dryRunBox.isChecked()) {
                    log("DRY_RUN=true -> not sent.");
                } else {
                    sendPhoto(botTokenText.getText().toString().trim(), channelIdText.getText().toString().trim(), imageUrl, caption);
                    posted.add(item.id);
                    savePostedIds(posted);
                    log("✓ Sent.");
                }
                if (pos < items.size() - 1) {
                    log("Waiting " + intervalMin + " minutes...");
                    for (int sec = 0; sec < intervalMin * 60; sec++) {
                        if (stopRequested) break;
                        Thread.sleep(1000);
                    }
                }
            }
            log("✓ Done.");
        } catch (Exception e) {
            log("ERROR: " + e.getMessage());
            runOnUiThread(() -> showError("Posting error", e.getMessage()));
        } finally {
            isPosting = false;
        }
    }

    private static class PostItem {
        int index;
        Map<String, String> row;
        String id;
        PostItem(int index, Map<String, String> row, String id) { this.index = index; this.row = row; this.id = id; }
    }

    private List<PostItem> buildPostItems() {
        Set<String> posted = resetPostedBox.isChecked() ? new HashSet<>() : getPostedIds();
        List<PostItem> items = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            String id = getProductId(rows.get(i), i + 1);
            if (!posted.contains(id)) items.add(new PostItem(i + 1, rows.get(i), id));
        }
        if (randomOrderBox.isChecked()) Collections.shuffle(items, new Random());
        String limitRaw = limitText.getText().toString().trim();
        if (!limitRaw.isEmpty()) {
            int limit = parseInt(limitRaw, items.size());
            if (limit < items.size()) items = new ArrayList<>(items.subList(0, Math.max(0, limit)));
        }
        return items;
    }

    private String getProductId(Map<String, String> row, int idx) {
        String idCol = selectedSpinnerValue(idColumnSpinner);
        if (!idCol.isEmpty()) {
            String val = getValue(row, idCol);
            if (!val.isEmpty()) return val;
        }
        for (String field : extractPlaceholders(getTemplateText())) {
            String val = getValue(row, field);
            if (!val.isEmpty()) {
                String safe = val.replaceAll("\\W+", "_");
                return safe.substring(0, Math.min(120, safe.length()));
            }
        }
        return "post_" + idx;
    }

    private void sendPhoto(String botToken, String channelId, String imageUrl, String caption) throws Exception {
        URL url = new URL("https://api.telegram.org/bot" + botToken + "/sendPhoto");
        String data = "chat_id=" + enc(channelId) + "&photo=" + enc(imageUrl) + "&caption=" + enc(caption);
        byte[] body = data.getBytes(StandardCharsets.UTF_8);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(60000);
        conn.setReadTimeout(60000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        conn.setRequestProperty("Content-Length", String.valueOf(body.length));
        OutputStream os = conn.getOutputStream();
        os.write(body);
        os.close();
        int code = conn.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String response = readStream(is);
        if (code != 200 || !response.contains("\"ok\":true")) {
            throw new Exception("Telegram error: HTTP " + code + " - " + response);
        }
    }

    private String enc(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8");
    }

    private void saveSettings(boolean showMessage) {
        try {
            SharedPreferences.Editor e = prefs().edit();
            e.putString("file_name", selectedFileName);
            e.putString("file_uri", selectedFileUri);
            e.putString("image_column", selectedSpinnerValue(imageColumnSpinner));
            e.putString("id_column", selectedSpinnerValue(idColumnSpinner));
            e.putString("interval_minutes", intervalText == null ? "60" : intervalText.getText().toString());
            e.putString("limit_posts", limitText == null ? "" : limitText.getText().toString());
            e.putBoolean("dry_run", dryRunBox == null || dryRunBox.isChecked());
            e.putBoolean("random_order", randomOrderBox != null && randomOrderBox.isChecked());
            e.putBoolean("reset_posted", resetPostedBox != null && resetPostedBox.isChecked());
            e.putBoolean("remove_empty_lines", removeEmptyLinesBox != null && removeEmptyLinesBox.isChecked());
            e.putString("bot_token", botTokenText == null ? "" : botTokenText.getText().toString());
            e.putString("channel_id", channelIdText == null ? "" : channelIdText.getText().toString());
            e.putString("current_template_text", getTemplateText());
            e.putString("template_name", selectedTemplateName());
            JSONObject obj = new JSONObject();
            for (Map.Entry<String, String> ent : templates.entrySet()) obj.put(ent.getKey(), ent.getValue());
            e.putString("templates", obj.toString());
            e.apply();
            if (showMessage) alert("Saved", "Settings saved on this Android device.");
            log("Settings saved.");
        } catch (Exception ex) {
            log("Could not save settings: " + ex.getMessage());
        }
    }

    private void loadSettings() {
        SharedPreferences p = prefs();
        selectedFileName = p.getString("file_name", "");
        selectedFileUri = p.getString("file_uri", "");
        if (!selectedFileName.isEmpty()) filePathText.setText(selectedFileName);
        if (intervalText != null) intervalText.setText(p.getString("interval_minutes", "60"));
        if (limitText != null) limitText.setText(p.getString("limit_posts", ""));
        if (dryRunBox != null) dryRunBox.setChecked(p.getBoolean("dry_run", true));
        if (randomOrderBox != null) randomOrderBox.setChecked(p.getBoolean("random_order", false));
        if (resetPostedBox != null) resetPostedBox.setChecked(p.getBoolean("reset_posted", false));
        if (removeEmptyLinesBox != null) removeEmptyLinesBox.setChecked(p.getBoolean("remove_empty_lines", false));
        if (botTokenText != null) botTokenText.setText(p.getString("bot_token", ""));
        if (channelIdText != null) channelIdText.setText(p.getString("channel_id", ""));
        if (templateText != null) templateText.setText(p.getString("current_template_text", ""));
        templates.clear();
        try {
            JSONObject obj = new JSONObject(p.getString("templates", "{}"));
            JSONArray names = obj.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String name = names.getString(i);
                    templates.put(name, obj.getString(name));
                }
            }
        } catch (Exception ignored) { }
        refreshTemplateSpinner();
        String selected = p.getString("template_name", "");
        setSpinnerSelection(templateSpinner, new ArrayList<>(templates.keySet()), selected);
        onTemplateChanged();
    }

    private Set<String> getPostedIds() {
        return new HashSet<>(prefs().getStringSet("posted_ids", new HashSet<>()));
    }

    private void savePostedIds(Set<String> ids) {
        prefs().edit().putStringSet("posted_ids", new HashSet<>(ids)).apply();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private String getPrefString(String key, String def) {
        return prefs().getString(key, def);
    }

    private String getValue(Map<String, String> row, String column) {
        if (column == null || column.isEmpty()) return "";
        return cleanValue(row.get(column));
    }

    private String cleanValue(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value).trim();
        String low = s.toLowerCase(Locale.ROOT);
        if (low.equals("nan") || low.equals("none") || low.equals("null")) return "";
        return s;
    }

    private String rstrip(String s) {
        int i = s.length() - 1;
        while (i >= 0 && Character.isWhitespace(s.charAt(i))) i--;
        return s.substring(0, i + 1);
    }

    private byte[] readAllBytes(InputStream is) throws Exception {
        if (is == null) throw new Exception("Could not open file.");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) baos.write(buf, 0, n);
        is.close();
        return baos.toByteArray();
    }

    private String readStream(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        return sb.toString();
    }

    private String getFileName(Uri uri) {
        String result = "selected_file";
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) result = cursor.getString(idx);
                }
            } catch (Exception ignored) { }
        }
        return result == null ? "selected_file" : result;
    }

    private void setSpinnerValues(Spinner sp, List<String> values) {
        if (sp == null) return;
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(adapter);
    }

    private void setSpinnerSelection(Spinner sp, List<String> values, String value) {
        if (sp == null || values == null) return;
        int idx = values.indexOf(value);
        if (idx >= 0) sp.setSelection(idx);
    }

    private String selectedSpinnerValue(Spinner sp) {
        if (sp == null || sp.getSelectedItem() == null) return "";
        return sp.getSelectedItem().toString();
    }

    private int parseInt(String text, int def) {
        try { return Integer.parseInt(text.trim()); } catch (Exception e) { return def; }
    }

    private void showPage(int index) {
        activePage = index;
        pageData.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        pageWrite.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        pagePreview.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        pageSend.setVisibility(index == 3 ? View.VISIBLE : View.GONE);
        for (int i = 0; i < navButtons.size(); i++) {
            Button b = navButtons.get(i);
            if (i == index) {
                b.setTextColor(Color.WHITE);
                b.setBackground(round(ACCENT, dp(20), ACCENT, 1));
            } else {
                b.setTextColor(MUTED);
                b.setBackground(round(Color.TRANSPARENT, dp(20), Color.TRANSPARENT, 0));
            }
        }
    }

    private void log(String msg) {
        runOnUiThread(() -> {
            if (logBox != null) {
                logBox.append(msg + "\n");
                logBox.setSelection(logBox.getText().length());
            }
        });
    }

    private void showKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
    }

    private void showError(String title, String msg) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg == null ? "" : msg).setPositiveButton("OK", null).show();
    }

    private void alert(String title, String msg) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg == null ? "" : msg).setPositiveButton("OK", null).show();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setIncludeFontPadding(true);
        if (bold) tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return tv;
    }

    private TextView smallText(String text) {
        TextView tv = label(text, 11, MUTED, false);
        tv.setPadding(0, 0, 0, dp(8));
        return tv;
    }

    private EditText edit(String hint, boolean multiline) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setTextSize(13);
        e.setPadding(dp(14), 0, dp(14), 0);
        e.setBackground(round(SURFACE2, dp(14), BORDER, 1));
        e.setSingleLine(!multiline);
        if (multiline) {
            e.setGravity(Gravity.TOP | Gravity.START);
            e.setPadding(dp(14), dp(12), dp(14), dp(12));
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        }
        return e;
    }

    private Button button(String text, boolean accent) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setTextColor(accent ? Color.WHITE : TEXT);
        b.setBackground(round(accent ? ACCENT : SURFACE2, dp(14), accent ? ACCENT : BORDER, 1));
        b.setElevation(accent ? dp(2) : 0);
        return b;
    }

    private CheckBox check(String text) {
        CheckBox cb = new CheckBox(this);
        cb.setText(text);
        cb.setTextSize(12);
        cb.setTextColor(TEXT);
        cb.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        return cb;
    }

    private Spinner spinner(List<String> values) {
        Spinner sp = new Spinner(this);
        setSpinnerValues(sp, values);
        sp.setBackground(round(SURFACE, dp(10), Color.rgb(200, 216, 232), 1));
        return sp;
    }

    private LinearLayout card(String title) {
        LinearLayout card = vertical();
        card.setPadding(dp(16), dp(14), dp(16), dp(16));
        card.setBackground(round(SURFACE, dp(22), BORDER, 1));
        card.setElevation(dp(2));
        TextView titleView = label(title, 11, MUTED, true);
        titleView.setPadding(0, 0, 0, dp(10));
        card.addView(titleView, matchWrap());
        return card;
    }

    private void sectionTitle(LinearLayout parent, String icon, String title, String subtitle) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(2), dp(2), dp(8));
        parent.addView(row, matchWrap());
        TextView iconView = label(icon, 22, Color.WHITE, false);
        iconView.setGravity(Gravity.CENTER);
        iconView.setBackground(round(ACCENT, dp(16), 0, 0));
        row.addView(iconView, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout texts = vertical();
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMargins(dp(12), 0, 0, 0);
        row.addView(texts, textLp);
        texts.addView(label(title, 22, TEXT, true));
        texts.addView(label(subtitle, 11, MUTED, false));
    }

    private TextView heroChip(String text) {
        TextView tv = label(text, 11, Color.WHITE, true);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setBackground(round(Color.argb(55, 255, 255, 255), dp(16), Color.argb(65, 255, 255, 255), 1));
        return tv;
    }

    private LinearLayout.LayoutParams fullCardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(12));
        return lp;
    }

    private View separator() {
        View v = new View(this);
        v.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.setMargins(0, dp(10), 0, dp(10));
        v.setLayoutParams(lp);
        return v;
    }

    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private GradientDrawable round(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(radius);
        if (strokeWidth > 0) gd.setStroke(strokeWidth, strokeColor);
        return gd;
    }

    private LinearLayout.LayoutParams matchLinear() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private FrameLayout.LayoutParams matchFrame() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private ScrollView.LayoutParams matchScroll() {
        return new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchHeight(int h) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h);
        lp.setMargins(0, 0, 0, dp(8));
        return lp;
    }

    private LinearLayout.LayoutParams weightedCard(float weight, int l, int r, int t, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        lp.setMargins(dp(l), dp(t), dp(r), dp(b));
        return lp;
    }

    private LinearLayout.LayoutParams buttonLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(110), dp(46));
        lp.setMargins(0, 0, dp(6), 0);
        return lp;
    }

    private LinearLayout.LayoutParams weightedButton(float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), weight);
        lp.setMargins(0, 0, dp(6), 0);
        return lp;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextWatcherAdapter simpleWatcher(Runnable r) {
        return new TextWatcherAdapter(r);
    }

    private static class TextWatcherAdapter implements android.text.TextWatcher {
        private final Runnable runnable;
        TextWatcherAdapter(Runnable runnable) { this.runnable = runnable; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { if (runnable != null) runnable.run(); }
        @Override public void afterTextChanged(android.text.Editable s) { }
    }
}
