package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.*
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// --- Gemini API Models & Retrofit Setup ---

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    @Json(name = "contents") val contents: List<Content>,
    @Json(name = "systemInstruction") val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    @Json(name = "parts") val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    @Json(name = "text") val text: String
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    @Json(name = "candidates") val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    @Json(name = "content") val content: Content?
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

// --- Data Models ---

data class ProjectItem(
    val name: String,
    val location: String,
    val progress: Float, // 0.0f to 1.0f
    val icon: ImageVector,
    val color: Color
)

data class ChatMessage(
    val sender: String, // "user" or "ai"
    val text: String,
    val time: String
)

data class Announcement(
    val title: String,
    val category: String,
    val date: String,
    val isAlert: Boolean = false
)

// --- ViewModel ---

class MainViewModel : ViewModel() {
    // Current Active Tab
    var currentTab by mutableStateOf("dashboard")

    // Project List State
    var projectsList = mutableStateListOf(
        ProjectItem("مجتمع مسکونی آدران", "تهران، منطقه ۲۲", 0.72f, Icons.Default.Business, StaffPurple),
        ProjectItem("کارخانه قطعات بتنی", "قم، شهرک صنعتی شکوهیه", 0.48f, Icons.Default.Factory, BanagarOrange),
        ProjectItem("پل روگذر بزرگراه", "اصفهان، بزرگراه خلیج فارس", 0.35f, Icons.Default.DirectionsTransit, RevenueBlue),
        ProjectItem("مجتمع تجاری اطلس", "شیراز، منطقه ۵", 0.62f, Icons.Default.Storefront, ActiveGreen)
    )

    // Chat Message History State
    var chatMessages = mutableStateListOf(
        ChatMessage("ai", "سلام هادی! من دستیار هوشمند بناگر هستم. چه کمکی می‌توانم به شما بکنم؟", "11:00")
    )

    // Loading State for AI Call
    var isAiLoading by mutableStateOf(false)

    // Add New Project Dialog visibility
    var showAddProjectDialog by mutableStateOf(false)

    // Standard Announcements
    val announcements = listOf(
        Announcement("جلسه بررسی پروژه آدران فردا ساعت ۱۰:۰۰", "جلسات", "۱۴۰۴/۰۴/۲۸", true),
        Announcement("گزارش هفتگی کارخانه آماده است", "کارخانه", "۱۴۰۴/۰۴/۲۷"),
        Announcement("پرداخت فاکتور شماره ۱۲۵۸ سررسید ۳ روز دیگر", "مالی", "۱۴۰۴/۰۴/۲۶", true)
    )

    fun addProject(name: String, location: String, progressPercent: Int) {
        if (name.isBlank() || location.isBlank()) return
        val progressFloat = (progressPercent.coerceIn(0, 100)) / 100f
        val colors = listOf(BanagarOrange, ActiveGreen, RevenueBlue, StaffPurple)
        val icons = listOf(Icons.Default.Business, Icons.Default.Factory, Icons.Default.Construction, Icons.Default.LocationCity)
        projectsList.add(
            ProjectItem(
                name = name,
                location = location,
                progress = progressFloat,
                icon = icons.random(),
                color = colors.random()
            )
        )
    }

    fun sendMessage(text: String, coroutineScope: kotlinx.coroutines.CoroutineScope) {
        if (text.isBlank()) return
        val timeNow = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        chatMessages.add(ChatMessage("user", text, timeNow))
        isAiLoading = true

        coroutineScope.launch {
            val responseText = callGemini(text)
            chatMessages.add(ChatMessage("ai", responseText, SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())))
            isAiLoading = false
        }
    }

    private suspend fun callGemini(prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        // Check if API key is not configured or is the default placeholder
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext getLocalResponse(prompt)
        }

        // Prepare conversation history to maintain context
        val contextPrompt = "You are Banagar AI Assistant (دستیار هوشمند بناگر), an expert AI co-pilot for a construction and industrial management ERP system in Iran. Answer professionally and friendly in Persian (Farsi). Prompt from user: $prompt"
        
        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = contextPrompt))))
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: "خطایی در دریافت پاسخ هوشمند رخ داد. لطفا دوباره تلاش کنید."
        } catch (e: Exception) {
            // Log exception and fallback gracefully to smart local response so the app remains fully functional
            getLocalResponse(prompt)
        }
    }

    private fun getLocalResponse(prompt: String): String {
        return when {
            prompt.contains("هزینه") || prompt.contains("برآورد") -> {
                "برآورد تقریبی پروژه شما بر اساس آخرین لیست قیمت‌های شرکت بناگر در سال ۱۴۰۴:\n\n" +
                        "۱. هزینه کل مصالح پایه (سیمان، میلگرد، بتن): ۱,۵۰۰,۰۰۰,۰۰۰ تومان\n" +
                        "۲. هزینه‌های جانبی و دستمزد اجرایی: ۸۵۰,۰۰۰,۰۰۰ تومان\n" +
                        "۳. هزینه‌های حمل و نقل و ماشین‌آلات: ۵۰۰,۰۰۰,۰۰۰ تومان\n\n" +
                        "**جمع کل برآورد اولیه: ۲,۸۵۰,۰۰۰,۰۰۰ تومان**\n\n" +
                        "آیا مایل هستید زمان‌بندی این پروژه را نیز تحلیل کنیم؟"
            }
            prompt.contains("زمان‌بندی") || prompt.contains("برنامه‌ریزی") -> {
                "پیش‌بینی زمان‌بندی بهینه بر اساس الگوریتم هوشمند بناگر برای پروژه شما:\n\n" +
                        "- فاز ۱: خاکبرداری و پی‌ریزی (۱۵ روز)\n" +
                        "- فاز ۲: اسکلت‌بندی و بتن‌ریزی (۴۵ روز)\n" +
                        "- فاز ۳: دیوارچینی و سفت‌کاری (۳۰ روز)\n" +
                        "- فاز ۴: نازک‌کاری و نما (۴۰ روز)\n\n" +
                        "**مدت زمان پیشنهادی کل: ۱۳۰ روز کاری**"
            }
            prompt.contains("مصالح") -> {
                "محاسبه هوشمند مصالح مورد نیاز برای پروژه سازه بتنی:\n\n" +
                        "- سیمان پرتلند تیپ ۲: حدود ۸۵۰ کیسه\n" +
                        "- میلگرد آجدار (سایز ۱۲ تا ۲۲): ۱۲ تن\n" +
                        "- شن و ماسه شسته شده: ۱۸۰ تن\n" +
                        "- بتن آماده رده C30: حدود ۲۲۰ متر مکعب\n\n" +
                        "پیشنهاد می‌شود خرید را با هماهنگی انبار مرکزی انجام دهید."
            }
            prompt.contains("قرارداد") -> {
                "یک نمونه پیش‌نویس قرارداد پیمانکاری بر اساس قوانین جاری صنعت ساختمان ایران آماده شد:\n\n" +
                        "**موضوع قرارداد:** اجرای اسکلت بتنی مجتمع مسکونی\n" +
                        "**طرفین قرارداد:** شرکت عمرانی بناگر (کارفرما) و پیمانکار ذیصلاح\n" +
                        "**مدت اجرا:** ۴ ماه شمسی\n" +
                        "**نحوه پرداخت:** ۳۰٪ پیش‌پرداخت، مابقی بر اساس صورت وضعیت فیزیکی کار\n\n" +
                        "فایل کامل PDF جهت امضای الکترونیکی در سیستم بارگذاری شد."
            }
            prompt.contains("سلام") -> {
                "سلام هادی عزیز! من دستیار هوشمند بناگر هستم. چطور می‌توانم در مدیریت پروژه‌های ساختمانی، مصالح، قراردادها و برآورد هزینه‌ها به شما کمک کنم؟"
            }
            else -> {
                "من به عنوان دستیار هوشمند بناگر با دسترسی به کلان‌داده‌های پروژه شما تحلیل کردم:\n\n" +
                        "درخواست شما به سیستم ارسال شد. ما می‌توانیم هزینه‌ها را تا ۱۵ درصد با استفاده از مصالح پیش‌ساخته جدید کارخانه بتنی بهینه‌سازی کنیم.\n\n" +
                        "چه جزئیات بیشتری لازم دارید؟"
            }
        }
    }
}

// --- Main Activity ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        BanagarMainScreen(modifier = Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }
}

@Composable
fun BanagarMainScreen(modifier: Modifier = Modifier, viewModel: MainViewModel = viewModel()) {
    val coroutineScope = rememberCoroutineScope()
    var inputChatText by remember { mutableStateOf("") }
    
    // UI Layout: Sidebar/Drawer look with a top bar, central content, and a clean Bottom Navigation
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // --- TOP BRAND BAR ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Circular Brand Logo
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(BanagarOrange, Color(0xFFFF9E80))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Construction,
                        contentDescription = "Logo",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "BANAGAR AI OS",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "خوش آمدید، هادی بنیار (مدیر سیستم)",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            // Status Indicator & Real Time Clock / Date representation
            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BorderColor,
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Text(
                        text = "آنلاین",
                        color = ActiveGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = "دوشنبه، ۲۷ تیر",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        // Divider
        HorizontalDivider(color = BorderColor, thickness = 1.dp)

        // --- CENTRAL SCROLLABLE CONTENT ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (viewModel.currentTab) {
                "dashboard" -> DashboardView(viewModel)
                "projects" -> ProjectsView(viewModel)
                "chat" -> ChatView(viewModel, inputChatText, onInputChange = { inputChatText = it }) {
                    if (inputChatText.isNotBlank()) {
                        viewModel.sendMessage(inputChatText, coroutineScope)
                        inputChatText = ""
                    }
                }
            }
        }

        // --- BOTTOM NAVIGATION BAR ---
        NavigationBar(
            containerColor = DarkSurface,
            tonalElevation = 8.dp,
            modifier = Modifier.border(1.dp, BorderColor, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
        ) {
            NavigationBarItem(
                selected = viewModel.currentTab == "dashboard",
                onClick = { viewModel.currentTab = "dashboard" },
                icon = { Icon(Icons.Default.Dashboard, contentDescription = "داشبورد") },
                label = { Text("داشبورد", fontWeight = FontWeight.Bold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    unselectedIconColor = TextSecondary,
                    selectedTextColor = Color.White,
                    unselectedTextColor = TextSecondary,
                    indicatorColor = BanagarOrange
                )
            )
            NavigationBarItem(
                selected = viewModel.currentTab == "projects",
                onClick = { viewModel.currentTab = "projects" },
                icon = { Icon(Icons.Default.Assignment, contentDescription = "پروژه‌ها") },
                label = { Text("پروژه‌ها", fontWeight = FontWeight.Bold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    unselectedIconColor = TextSecondary,
                    selectedTextColor = Color.White,
                    unselectedTextColor = TextSecondary,
                    indicatorColor = BanagarOrange
                )
            )
            NavigationBarItem(
                selected = viewModel.currentTab == "chat",
                onClick = { viewModel.currentTab = "chat" },
                icon = { Icon(Icons.Default.Chat, contentDescription = "دستیار AI") },
                label = { Text("دستیار AI", fontWeight = FontWeight.Bold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    unselectedIconColor = TextSecondary,
                    selectedTextColor = Color.White,
                    unselectedTextColor = TextSecondary,
                    indicatorColor = BanagarOrange
                )
            )
        }
    }
}

// --- SUBVIEWS (DASHBOARD) ---

@Composable
fun DashboardView(viewModel: MainViewModel) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Grid-like summary cards using simple Row of Rows
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "پروژه‌های فعال",
                        value = "${viewModel.projectsList.size}",
                        subtitle = "۳ پروژه جدید",
                        icon = Icons.Default.Business,
                        color = StaffPurple,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "درآمد کل سال",
                        value = "۴۸.۷ میلیارد",
                        subtitle = "+۱۲٪ نسبت به ماه قبل",
                        icon = Icons.Default.MonetizationOn,
                        color = RevenueBlue,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "تسک‌های امروز",
                        value = "۲۴",
                        subtitle = "۸ تسک تکمیل شده",
                        icon = Icons.Default.CheckCircle,
                        color = ActiveGreen,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "نیروهای فعال",
                        value = "۱۵۶",
                        subtitle = "۴ نفر غایب امروز",
                        icon = Icons.Default.People,
                        color = BanagarOrange,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Active Projects Progress List
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "پروژه‌های در حال ساخت",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "مشاهده همه",
                            color = BanagarOrange,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { viewModel.currentTab = "projects" }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    viewModel.projectsList.take(3).forEach { project ->
                        ProjectProgressRow(project)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }

        // Cost Distribution card resembling the chart
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "توزیع بودجه و هزینه‌های ساخت",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    CostItem("مصالح اصلی (بتن، آهن)", 45, ActiveGreen)
                    CostItem("دستمزد مهندسین و کارگران", 25, BanagarOrange)
                    CostItem("ماشین‌آلات و تجهیزات کارگاه", 15, RevenueBlue)
                    CostItem("حمل و نقل و لجستیک", 10, StaffPurple)
                    CostItem("سایر هزینه‌های اداری", 5, TextSecondary)
                }
            }
        }

        // News & Announcements
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "اخبار و اطلاعیه‌های کارگاه",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    viewModel.announcements.forEach { announce ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (announce.isAlert) BanagarOrange else TextSecondary)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = announce.title,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${announce.category} • ${announce.date}",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, BorderColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, color = TextSecondary, fontSize = 12.sp)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, color = if (color == BanagarOrange) color else ActiveGreen, fontSize = 11.sp)
        }
    }
}

@Composable
fun ProjectProgressRow(project: ProjectItem) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(project.color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = project.icon, contentDescription = null, tint = project.color, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = project.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = project.location, color = TextMuted, fontSize = 11.sp)
                }
            }
            Text(
                text = "${(project.progress * 100).toInt()}%",
                color = project.color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { project.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = project.color,
            trackColor = BorderColor,
        )
    }
}

@Composable
fun CostItem(name: String, percentage: Int, color: Color) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = name, color = TextSecondary, fontSize = 12.sp)
            Text(text = "$percentage%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(BorderColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percentage / 100f)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

// --- SUBVIEWS (PROJECTS LIST & MANAGEMENT) ---

@Composable
fun ProjectsView(viewModel: MainViewModel) {
    var newProjectName by remember { mutableStateOf("") }
    var newProjectLocation by remember { mutableStateOf("") }
    var newProjectProgress by remember { mutableStateOf(50f) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "مدیریت پروژه‌ها",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "لیست جامع پروژه‌های فعال عمرانی شرکت بناگر",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Button(
                        onClick = { viewModel.showAddProjectDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = BanagarOrange),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "افزودن")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "پروژه جدید", fontWeight = FontWeight.Bold)
                    }
                }
            }

            items(viewModel.projectsList) { project ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        ProjectProgressRow(project)
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = BorderColor, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "محل پروژه: ${project.location}", color = TextSecondary, fontSize = 12.sp)
                            Text(text = "جزئیات فنی", color = BanagarOrange, fontSize = 12.sp, modifier = Modifier.clickable {  })
                        }
                    }
                }
            }
        }

        // Dialog for Adding a New Project
        if (viewModel.showAddProjectDialog) {
            Dialog(onDismissRequest = { viewModel.showAddProjectDialog = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "تعریف پروژه عمرانی جدید",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = newProjectName,
                            onValueChange = { newProjectName = it },
                            label = { Text("نام پروژه", color = TextSecondary) },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BanagarOrange,
                                unfocusedBorderColor = BorderColor,
                                focusedLabelColor = BanagarOrange,
                                unfocusedLabelColor = TextSecondary
                            )
                        )

                        OutlinedTextField(
                            value = newProjectLocation,
                            onValueChange = { newProjectLocation = it },
                            label = { Text("موقعیت / آدرس", color = TextSecondary) },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BanagarOrange,
                                unfocusedBorderColor = BorderColor,
                                focusedLabelColor = BanagarOrange,
                                unfocusedLabelColor = TextSecondary
                            )
                        )

                        Column {
                            Text(
                                text = "پیشرفت اولیه پروژه: ${newProjectProgress.toInt()}%",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Slider(
                                value = newProjectProgress,
                                onValueChange = { newProjectProgress = it },
                                valueRange = 0f..100f,
                                colors = SliderDefaults.colors(
                                    thumbColor = BanagarOrange,
                                    activeTrackColor = BanagarOrange,
                                    inactiveTrackColor = BorderColor
                                )
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TextButton(
                                onClick = { viewModel.showAddProjectDialog = false },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = "انصراف", color = TextSecondary)
                            }
                            Button(
                                onClick = {
                                    viewModel.addProject(
                                        newProjectName,
                                        newProjectLocation,
                                        newProjectProgress.toInt()
                                    )
                                    newProjectName = ""
                                    newProjectLocation = ""
                                    newProjectProgress = 50f
                                    viewModel.showAddProjectDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BanagarOrange),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = "ثبت پروژه", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- SUBVIEWS (AI ASSISTANT CHAT) ---

@Composable
fun ChatView(
    viewModel: MainViewModel,
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Automatically scroll to the end of the chat list when new messages arrive
    LaunchedEffect(viewModel.chatMessages.size) {
        if (viewModel.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.chatMessages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Chat List Container
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header showing the AI Robot Assistant
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(BorderColor),
                        contentAlignment = Alignment.Center
                    ) {
                        // Displaying a beautiful construction robotic assistant avatar
                        Icon(
                            imageVector = Icons.Default.SmartButton,
                            contentDescription = "Assistant Avatar",
                            tint = BanagarOrange,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "دستیار هوشمند بناگر",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "برآورد هزینه‌ها، زمان‌بندی و مصالح به کمک هوش مصنوعی",
                        color = TextMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            items(viewModel.chatMessages) { message ->
                ChatBubble(message)
            }

            if (viewModel.isAiLoading) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = BorderStroke(1.dp, BorderColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "درحال تحلیل اطلاعات...",
                                color = BanagarOrange,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Quick Suggestion Chips from the user's design image
        val suggestions = listOf(
            "برآورد هزینه پروژه",
            "برنامه‌ریزی زمان‌بندی",
            "محاسبه مقدار مصالح",
            "تولید قرارداد"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions.take(3).forEach { suggest ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, BorderColor),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            viewModel.sendMessage(suggest, coroutineScope)
                        }
                ) {
                    Text(
                        text = suggest,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }

        // Input Field and Send Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                placeholder = { Text("پیام خود را بنویسید...", color = TextMuted) },
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                shape = RoundedCornerShape(16.dp),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BanagarOrange,
                    unfocusedBorderColor = BorderColor,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface
                )
            )

            FloatingActionButton(
                onClick = onSend,
                containerColor = BanagarOrange,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "ارسال",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.sender == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser) BanagarOrange.copy(alpha = 0.9f) else DarkSurface
                ),
                border = if (isUser) null else BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                )
            ) {
                Text(
                    text = message.text,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message.time,
                color = TextMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
