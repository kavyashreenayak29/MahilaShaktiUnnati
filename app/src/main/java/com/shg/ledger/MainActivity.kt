package com.shg.ledger


import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

// --- Premium Color Palette ---
val Sage = Color(0xFF5A7D6E)
val SageLight = Color(0xFFE0E6E3)
val Earth = Color(0xFFC88D67)
val EarthLight = Color(0xFFF0EDE8)
val SoftBg = Color(0xFFFBF9F4)
val TextDark = Color(0xFF2D2D2D)
val TextMuted = Color(0xFF6B6B6B)
val CardWhite = Color(0xFFFFFFFF)
val DividerColor = Color(0xFFEDE9E1)

// --- Logo Brand Colors (From User Image) ---
val BrandPink = Color(0xFFE91E63)
val BrandPurple = Color(0xFF4A148C)
val BrandGold = Color(0xFFFFD700)

class ShgViewModel(private val dao: ShgDao, private val groupDao: SHGGroupDao) : ViewModel() {
    val group = groupDao.getGroup().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )
    val members = dao.getAllMembers().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
    val savings = dao.getAllSavings().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
    val loans = dao.getAllLoans().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
    val attendance = dao.getAllAttendance().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
    val transactions = dao.getAllTransactions().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    fun addMember(name: String, phone: String, photoUrl: String? = null) {
        viewModelScope.launch {
            val count = members.value.size + 1
            val code = "SHG-${count.toString().padStart(3, '0')}"
            dao.insertMember(Member(name = name, phone = phone, memberCode = code, photoUrl = photoUrl))
        }
    }

    fun updateMember(id: String, name: String, phone: String, photoUrl: String?) {
        viewModelScope.launch {
            val member = members.value.find { it.id == id }?.copy(name = name, phone = phone, photoUrl = photoUrl)
            member?.let { dao.insertMember(it) }
        }
    }

    fun deleteMember(id: String) {
        viewModelScope.launch {
            val member = members.value.find { it.id == id }
            member?.let { dao.deleteMember(it) }
        }
    }

    fun repayPrincipal(loanId: String, amount: Double) {
        viewModelScope.launch {
            val currentLoans = loans.value
            val loan = currentLoans.find { it.id == loanId }
            loan?.let {
                val totalInterest = (it.principalAmount * it.interestRate * it.durationYears) / 100.0
                val totalPayable = it.principalAmount + totalInterest

                // Proportionate split
                val principalRatio = it.principalAmount / totalPayable
                val interestRatio = totalInterest / totalPayable

                val pPaid = amount * principalRatio
                val iPaid = amount * interestRatio

                val newRepaidP = Math.min(it.principalAmount, it.repaidPrincipal + pPaid)
                val newRepaidI = Math.min(totalInterest, it.repaidInterest + iPaid)

                val updated = it.copy(
                    repaidPrincipal = newRepaidP,
                    repaidInterest = newRepaidI,
                    status = if ((newRepaidP + newRepaidI) >= totalPayable - 1.0) "Closed" else "Active",
                    updatedAt = System.currentTimeMillis()
                )
                dao.updateLoan(updated)
                dao.insertTransaction(TransactionRecord(
                    memberId = it.memberId,
                    type = "Loan Repayment",
                    amount = amount,
                    referenceId = loanId
                ))
            }
        }
    }

    fun toggleAttendance(memberId: String, week: Int) {
        viewModelScope.launch {
            val currentAttendance = attendance.value
            val existing = currentAttendance.find { it.memberId == memberId && it.weekNumber == week }
            if (existing != null) {
                dao.insertAttendance(existing.copy(status = if (existing.status == "Present") "Absent" else "Present"))
            } else {
                dao.insertAttendance(AttendanceRecord(memberId = memberId, weekNumber = week, status = "Present"))
            }
        }
    }

    fun addSavings(memberId: String, amount: Double, week: Int) {
        viewModelScope.launch {
            val existing = savings.value.find { it.memberId == memberId && it.weekNumber == week }
            if (existing != null) {
                if (existing.status == "Paid") {
                    // Logic for "unpaying" if needed, or just return
                    return@launch
                }
            }

            val recordId = UUID.randomUUID().toString()
            dao.insertSavings(SavingsRecord(id = recordId, memberId = memberId, amount = amount, weekNumber = week, status = "Paid"))
            dao.insertTransaction(TransactionRecord(
                memberId = memberId,
                type = "Savings",
                amount = amount,
                referenceId = recordId
            ))
        }
    }

    fun calculateInterest(principal: Double, years: Double): Double {
        return (principal * 15.0 * years) / 100.0
    }

    fun calculateWeeklyEMI(principal: Double, years: Double): Double {
        val interest = calculateInterest(principal, years)
        val totalAmount = principal + interest
        val weeks = (years * 52).toInt()
        return if (weeks > 0) totalAmount / weeks else 0.0
    }

    fun disburseLoan(memberId: String, amount: Double, years: Double) {
        viewModelScope.launch {
            val currentLoans = loans.value
            val hasActiveLoan = currentLoans.any { it.memberId == memberId && it.status == "Active" }
            if (hasActiveLoan) return@launch

            val totalWeeks = (years * 52).toInt()
            val emi = calculateWeeklyEMI(amount, years)

            val loan = LoanRecord(
                memberId = memberId,
                principalAmount = amount,
                interestRate = 15.0,
                durationYears = years,
                totalWeeks = totalWeeks,
                weeklyEmi = emi,
                startDate = System.currentTimeMillis()
            )
            dao.insertLoan(loan)
            dao.insertTransaction(TransactionRecord(
                memberId = memberId,
                type = "Loan Disbursement",
                amount = amount,
                referenceId = loan.id
            ))
        }
    }

    fun createGroup(name: String, code: String?, savingsAmount: Double) {
        viewModelScope.launch {
            groupDao.insertGroup(SHGGroup(
                groupName = name,
                groupCode = if (code.isNullOrBlank()) null else code,
                weeklySavingsAmount = savingsAmount
            ))
        }
    }
}

class ShgViewModelFactory(private val dao: ShgDao, private val groupDao: SHGGroupDao) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShgViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ShgViewModel(dao, groupDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShgTheme {
                ShgApp()
            }
        }
    }
}

@Composable
fun ShgTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Sage,
            onPrimary = Color.White,
            secondary = Earth,
            onSecondary = Color.White,
            background = SoftBg,
            surface = CardWhite
        ),
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.Serif),
            headlineMedium = TextStyle(fontFamily = FontFamily.Serif),
            headlineSmall = TextStyle(fontFamily = FontFamily.Serif)
        ),
        content = content
    )
}

@Composable
fun ShgApp() {
    val context = LocalContext.current
    val db = ShgDatabase.getDatabase(context)
    val vm: ShgViewModel = viewModel(factory = ShgViewModelFactory(db.dao(), db.groupDao()))

    val shgGroup by vm.group.collectAsState()

    var lang by remember { mutableStateOf<Language>(Language.English) }
    var currentTab by remember { mutableStateOf("Dashboard") }
    var showAddMember by remember { mutableStateOf(false) }

    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
        showSplash = false
    }

    if (showSplash) {
        SplashScreen(lang)
    } else {
        if (shgGroup == null) {
            GroupSetupScreen(lang, onSetup = { name, code, savings ->
                vm.createGroup(name, code, savings)
            })
        } else {
            Scaffold(
                topBar = {
                    Header(
                        groupName = shgGroup?.groupName ?: lang.appName,
                        lang = lang,
                        onLangToggle = { lang = if (lang is Language.English) Language.Kannada else Language.English }
                    )
                },
                bottomBar = {
                    BottomNav(currentTab, lang, onTabSelect = { currentTab = it })
                }
            ) { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues).fillMaxSize().background(SoftBg)) {
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            (fadeIn() + slideInHorizontally { it / 2 }) togetherWith (fadeOut() + slideOutHorizontally { -it / 2 })
                        },
                        label = "TabTransition"
                    ) { targetTab ->
                        when (targetTab) {
                            "Dashboard" -> DashboardScreen(vm, lang, onTabSwitch = { currentTab = it })
                            "Members" -> MembersScreen(vm, lang, onAddClick = { showAddMember = true })
                            "Savings" -> SavingsScreen(vm, lang)
                            "Loans" -> LoansScreen(vm, lang)
                        }
                    }

                    if (showAddMember) {
                        AddMemberDialog(lang, onDismiss = { showAddMember = false }, onAdd = { n, p, u ->
                            vm.addMember(n, p, u)
                            showAddMember = false
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun SplashScreen(lang: Language) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Stylized Logo using Shapes
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
                Canvas(modifier = Modifier.size(140.dp)) {
                    drawCircle(color = BrandPink.copy(alpha = 0.1f), radius = size.minDimension / 1.4f)
                    drawCircle(color = BrandPurple.copy(alpha = 0.05f), radius = size.minDimension / 1.8f)
                }
                Surface(
                    shape = CircleShape,
                    color = BrandPurple,
                    modifier = Modifier.size(80.dp),
                    shadowElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = "App Logo",
                            modifier = Modifier.size(52.dp),
                            tint = Color.White
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = BrandGold,
                    modifier = Modifier.size(32.dp).offset(y = 35.dp, x = 25.dp),
                    shadowElevation = 4.dp,
                    border = BorderStroke(2.dp, Color.White)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CurrencyRupee,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = BrandPurple
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(
                text = lang.appName,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color = BrandPurple
            )
            Spacer(Modifier.height(12.dp))
            Surface(
                color = BrandPink.copy(alpha = 0.1f),
                shape = RoundedCornerShape(100.dp)
            ) {
                Text(
                    text = "DIGNITY • GROWTH • UNITY",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = BrandPink,
                    letterSpacing = 1.5.sp
                )
            }
            Spacer(Modifier.height(64.dp))
            CircularProgressIndicator(color = BrandPink, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
        }

        Text(
            text = "By SHG Federation",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            fontSize = 12.sp,
            color = TextMuted,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun GroupSetupScreen(lang: Language, onSetup: (String, String, Double) -> Unit) {
    var groupName by remember { mutableStateOf("") }
    var groupCode by remember { mutableStateOf("") }
    var savingsAmount by remember { mutableStateOf("100") }

    val isCodeValid = groupCode.length == 4 && groupCode.all { it.isDigit() }
    val isSavingsValid = savingsAmount.toDoubleOrNull()?.let { it >= 10 && it <= 1000 } ?: false
    val canProceed = groupName.isNotBlank() && isCodeValid && isSavingsValid

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftBg),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = CardWhite,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Sage.copy(alpha = 0.1f),
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Groups, null, tint = Sage, modifier = Modifier.size(40.dp))
                    }
                }

                Text(
                    text = lang.groupSetup,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    color = Sage
                )

                Text(
                    text = lang.enterGroupName,
                    fontSize = 14.sp,
                    color = TextMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                OutlinedTextField(
                    value = groupName,
                    onValueChange = { if (it.length <= 30) groupName = it },
                    label = { Text(lang.groupNameHint) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = groupCode,
                    onValueChange = {
                        if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                            groupCode = it
                        }
                    },
                    label = { Text(lang.groupCodeHint) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    singleLine = true,
                    supportingText = {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            if (groupCode.isNotEmpty() && !isCodeValid) {
                                Text(lang.invalidCodeError, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            } else {
                                Spacer(Modifier.width(1.dp))
                            }
                            Text("${groupCode.length}/4", fontSize = 11.sp, color = if (isCodeValid) Sage else TextMuted)
                        }
                    },
                    isError = groupCode.isNotEmpty() && !isCodeValid
                )

                OutlinedTextField(
                    value = savingsAmount,
                    onValueChange = {
                        if (it.length <= 5 && (it.isEmpty() || it.all { char -> char.isDigit() })) {
                            savingsAmount = it
                        }
                    },
                    label = { Text(lang.weeklySavingsLabel) },
                    placeholder = { Text(lang.weeklySavingsHint) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    singleLine = true,
                    isError = savingsAmount.isNotEmpty() && !isSavingsValid,
                    prefix = { Text("₹ ") }
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (canProceed) {
                            onSetup(groupName, groupCode, savingsAmount.toDoubleOrNull() ?: 100.0)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = canProceed
                ) {
                    Text(lang.createGroup, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun Header(groupName: String, lang: Language, onLangToggle: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Sage,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Branded Icon in Top Bar
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "Logo",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SHG LEDGER",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = groupName,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                )
            }
            Surface(
                onClick = onLangToggle,
                color = Color.Black.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Translate, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Text(
                        text = if (lang is Language.English) "ಕನ್ನಡ" else "English",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun BottomNav(currentTab: String, lang: Language, onTabSelect: (String) -> Unit) {
    NavigationBar(
        containerColor = CardWhite,
        tonalElevation = 12.dp,
        modifier = Modifier.height(84.dp)
    ) {
        val navItems = listOf(
            Triple("Dashboard", lang.dashboard, Icons.Default.GridView),
            Triple("Members", lang.members, Icons.Default.Groups),
            Triple("Savings", lang.ledger, Icons.Default.AccountBalanceWallet),
            Triple("Loans", lang.loans, Icons.Default.Handshake)
        )

        navItems.forEach { (id, label, icon) ->
            val selected = currentTab == id
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelect(id) },
                icon = { Icon(icon, null, modifier = Modifier.size(24.dp)) },
                label = {
                    Text(
                        label.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Sage,
                    selectedTextColor = Sage,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted
                )
            )
        }
    }
}

@Composable
fun DashboardScreen(vm: ShgViewModel, lang: Language, onTabSwitch: (String) -> Unit) {
    val members by vm.members.collectAsState()
    val savings by vm.savings.collectAsState()
    val loans by vm.loans.collectAsState()
    val context = LocalContext.current

    val totalSavings = savings.sumOf { it.amount }
    val totalLoansOut = loans.filter { it.status == "Active" }.sumOf {
        val totalInterest = (it.principalAmount * it.interestRate * it.durationYears) / 100.0
        val totalPayable = it.principalAmount + totalInterest
        totalPayable - (it.repaidPrincipal + it.repaidInterest)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardCard(
                    label = lang.groupSavings,
                    value = "₹${totalSavings.toInt()}",
                    color = Sage,
                    icon = Icons.Default.AccountBalanceWallet,
                    isInverse = false
                )
                DashboardCard(
                    label = lang.totalLoansOut,
                    value = "₹${totalLoansOut.toInt()}",
                    color = Earth,
                    icon = Icons.Default.Handshake,
                    isInverse = false
                )
            }
        }

        item {
            SparklineCard(savings, lang)
        }

        item {
            TopSaversList(members, savings, lang)
        }

        item {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Add, null, tint = Sage, modifier = Modifier.size(20.dp))
                    Text(lang.quickActions, color = Sage, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ActionTile(lang.ledger, Icons.Default.AccountBalanceWallet, Modifier.weight(1f)) { onTabSwitch("Savings") }
                    ActionTile(lang.loans, Icons.Default.Handshake, Modifier.weight(1f)) { onTabSwitch("Loans") }
                }
            }
        }

        item {
            Box(modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                Button(
                    onClick = {
                        val report = """
                            ${lang.appName} REPORT
                            -----------------
                            ${lang.groupSavings}: ₹${totalSavings.toInt()}
                            ${lang.totalLoansOut}: ₹${totalLoansOut.toInt()}
                            -----------------
                            Generated on: ${java.util.Date()}
                        """.trimIndent()
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, report)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, null)
                        context.startActivity(shareIntent)
                    },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Earth),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Share, null)
                        Text(lang.export, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardCard(label: String, value: String, color: Color, icon: ImageVector, isInverse: Boolean) {
    Surface(
        color = if (isInverse) color else CardWhite,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
        border = if (!isInverse) BorderStroke(1.dp, DividerColor) else null,
        shadowElevation = if (isInverse) 8.dp else 2.dp
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(16.dp),
                color = if (isInverse) Color.White.copy(alpha = 0.2f) else color.copy(alpha = 0.08f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = if (isInverse) Color.White else color, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(20.dp))
            Column {
                Text(
                    label.uppercase(),
                    color = if (isInverse) Color.White.copy(alpha = 0.7f) else TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    value,
                    color = if (isInverse) Color.White else color,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif
                )
            }
        }
    }
}

@Composable
fun SparklineCard(savings: List<SavingsRecord>, lang: Language) {
    val chartData = remember(savings) {
        val sortedSavings = savings.filter { it.status == "Paid" }.sortedBy { it.weekNumber }
        val weeks = sortedSavings.map { it.weekNumber }.distinct()

        var cumulative = 0.0
        val data = weeks.map { w ->
            val weekTotal = sortedSavings.filter { it.weekNumber == w }.sumOf { it.amount }
            cumulative += weekTotal
            w to cumulative
        }

        if (data.isEmpty()) {
            emptyList<Pair<Int, Double>>()
        } else if (data.size == 1) {
            val (week, amount) = data[0]
            listOf((week - 1) to 0.0, week to amount)
        } else {
            data
        }
    }

    Surface(
        color = CardWhite,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        border = BorderStroke(1.dp, DividerColor)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.TrendingUp, null, tint = Sage, modifier = Modifier.size(18.dp))
                Text(lang.savingsTrend.uppercase(), color = Sage, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            }
            Spacer(Modifier.height(32.dp))
            Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                if (chartData.isEmpty()) {
                    Text("No data yet", modifier = Modifier.align(Alignment.Center), fontSize = 12.sp, color = TextMuted)
                } else {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val maxAmount = chartData.maxOfOrNull { it.second }?.toFloat() ?: 1f
                        val minWeek = chartData.minOf { it.first }
                        val maxWeek = chartData.maxOf { it.first }
                        val weekRange = (maxWeek - minWeek).coerceAtLeast(1)

                        val points = chartData.map { (week, amount) ->
                            val x = (week - minWeek).toFloat() / weekRange * size.width
                            val y = size.height - (amount.toFloat() / maxAmount) * size.height * 0.8f
                            Offset(x, y)
                        }

                        val path = Path().apply {
                            moveTo(points[0].x, points[0].y)
                            for (i in 1 until points.size) {
                                lineTo(points[i].x, points[i].y)
                            }
                        }
                        drawPath(path, Sage, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

                        val fillPath = Path().apply {
                            moveTo(points[0].x, size.height)
                            points.forEach { lineTo(it.x, it.y) }
                            lineTo(points.last().x, size.height)
                            close()
                        }
                        drawPath(fillPath, Sage.copy(alpha = 0.1f))
                    }
                }
            }
        }
    }
}

@Composable
fun TopSaversList(members: List<Member>, savings: List<SavingsRecord>, lang: Language) {
    Surface(
        color = CardWhite,
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        border = BorderStroke(1.dp, DividerColor)
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val sortedSavers = members.map { member ->
                val total = savings.filter { it.memberId == member.id }.sumOf { it.amount }
                member to total
            }.sortedByDescending { it.second }.take(5)

            sortedSavers.forEachIndexed { idx, (member, total) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = EarthLight
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text((idx + 1).toString(), fontWeight = FontWeight.Bold, color = Earth, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(member.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = TextDark, fontSize = 16.sp)
                    Text("₹${total.toInt()}", fontWeight = FontWeight.Bold, color = Sage, fontFamily = FontFamily.Serif, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun ActionTile(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        color = CardWhite,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.height(110.dp),
        border = BorderStroke(1.dp, DividerColor),
        shadowElevation = 1.dp,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = TextMuted, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
        }
    }
}

@Composable
fun MembersScreen(vm: ShgViewModel, lang: Language, onAddClick: () -> Unit) {
    val members by vm.members.collectAsState()
    val savings by vm.savings.collectAsState()
    val loans by vm.loans.collectAsState()
    val attendance by vm.attendance.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var memberToEdit by remember { mutableStateOf<Member?>(null) }
    var memberToShowDetails by remember { mutableStateOf<Member?>(null) }
    var memberToDelete by remember { mutableStateOf<Member?>(null) }

    val filtered = members.filter { it.name.contains(searchQuery, ignoreCase = true) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    lang.members,
                    modifier = Modifier.weight(1f),
                    fontSize = 28.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = Sage
                )
                Button(
                    onClick = onAddClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Sage),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, null)
                    Spacer(Modifier.width(8.dp))
                    Text(lang.addMember, fontWeight = FontWeight.Bold)
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(lang.search) },
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Sage,
                    unfocusedBorderColor = DividerColor
                )
            )

            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filtered) { member ->
                    MemberTile(
                        member = member,
                        onClick = { memberToShowDetails = member },
                        onEdit = { memberToEdit = member },
                        onDelete = { memberToDelete = member }
                    )
                }
            }
        }

        if (memberToEdit != null) {
            AddMemberDialog(
                lang = lang,
                onDismiss = { memberToEdit = null },
                onAdd = { n, p, u ->
                    vm.updateMember(memberToEdit!!.id, n, p, u)
                    memberToEdit = null
                },
                initialMember = memberToEdit
            )
        }

        val mTransactions by vm.transactions.collectAsState()
        if (memberToShowDetails != null) {
            MemberDetailsDialog(
                lang = lang,
                member = memberToShowDetails!!,
                savings = savings.filter { it.memberId == memberToShowDetails!!.id },
                loans = loans.filter { it.memberId == memberToShowDetails!!.id },
                attendance = attendance.filter { it.memberId == memberToShowDetails!!.id },
                transactions = mTransactions.filter { it.memberId == memberToShowDetails!!.id },
                onDismiss = { memberToShowDetails = null }
            )
        }

        if (memberToDelete != null) {
            DeleteConfirmationDialog(
                lang = lang,
                onDismiss = { memberToDelete = null },
                onConfirm = {
                    vm.deleteMember(memberToDelete!!.id)
                    memberToDelete = null
                }
            )
        }
    }
}

@Composable
fun DeleteConfirmationDialog(lang: Language, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = Earth.copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Delete, null, tint = Earth, modifier = Modifier.size(32.dp))
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "Are you sure you want to remove this member?",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = TextDark,
                    fontFamily = FontFamily.Serif
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SoftBg, contentColor = TextDark),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Cancel", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f).height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Earth),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        },
        shape = RoundedCornerShape(40.dp),
        containerColor = CardWhite
    )
}

@Composable
fun MemberTile(member: Member, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Surface(
        color = CardWhite,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, DividerColor),
        onClick = onClick
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(16.dp),
                color = SageLight
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (member.photoUrl != null) {
                        AsyncImage(
                            model = member.photoUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(member.name[0].toString(), fontWeight = FontWeight.Bold, color = Sage, fontSize = 24.sp)
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(member.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
                    Surface(color = SoftBg, shape = RoundedCornerShape(4.dp)) {
                        Text(member.memberCode, modifier = Modifier.padding(horizontal = 4.dp), fontSize = 9.sp, fontWeight = FontWeight.Black, color = Sage)
                    }
                }
                Text(member.phone, fontSize = 12.sp, color = TextMuted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, null, tint = Sage.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = Earth.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun MemberDetailsDialog(
    lang: Language,
    member: Member,
    savings: List<SavingsRecord>,
    loans: List<LoanRecord>,
    attendance: List<AttendanceRecord>,
    transactions: List<TransactionRecord>,
    onDismiss: () -> Unit
) {
    val totalSavings = savings.sumOf { it.amount }
    val attendanceRate = if (attendance.isEmpty()) 0 else {
        val presentCount = attendance.count { it.status == "Present" }
        val totalDays = attendance.size
        ((presentCount.toFloat() / totalDays) * 100).toInt()
    }
    val context = LocalContext.current

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SoftBg.copy(alpha = 0.98f)
        ) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = SageLight
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (member.photoUrl != null) {
                                    AsyncImage(
                                        model = member.photoUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(member.name[0].toString(), fontWeight = FontWeight.Bold, color = Sage, fontSize = 24.sp)
                                }
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(member.name, fontWeight = FontWeight.Bold, fontSize = 28.sp, fontFamily = FontFamily.Serif, color = TextDark)
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.background(CardWhite, CircleShape).border(1.dp, DividerColor, CircleShape)
                    ) {
                        Icon(Icons.Default.Close, null, tint = TextDark)
                    }
                }

                Spacer(Modifier.height(32.dp))

                // --- Impact Section: Financial Literacy / Credit Score ---
                Surface(
                    color = Sage.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, Sage.copy(alpha = 0.1f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Sage, shape = CircleShape, modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("A+", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(lang.impact.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Black, color = Sage)
                            Text(lang.onTime, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        border = BorderStroke(1.dp, DividerColor)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(lang.allTimeSavings.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                            Text("₹${totalSavings.toInt()}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Sage)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    border = BorderStroke(1.dp, DividerColor)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(lang.attendanceRate.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Text("$attendanceRate%", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Sage)
                        LinearProgressIndicator(
                            progress = attendanceRate / 100f,
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            color = Sage,
                            trackColor = SageLight
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
                Text("⚡ ${lang.transactionHistory.uppercase()}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextMuted)
                Spacer(Modifier.height(16.dp))

                if (transactions.isEmpty()) {
                    Text("No transactions recorded yet", fontSize = 14.sp, color = TextMuted, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    transactions.sortedByDescending { it.date }.forEach { tr ->
                        val prefix = when(tr.type) {
                            "Savings", "Loan Repayment" -> "+"
                            "Loan Disbursement" -> "-"
                            else -> ""
                        }
                        TransactionItem(tr.type, "$prefix₹${tr.amount.toInt()}", Date(tr.date))
                    }
                }

                Spacer(Modifier.height(32.dp))
                Text("💸 ${lang.loans.uppercase()}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextMuted)
                Spacer(Modifier.height(16.dp))

                loans.filter { it.status == "Active" }.forEach { ActiveLoanItem(it, lang) }

                if (loans.none { it.status == "Active" }) {
                    Text(lang.noActiveLoans, fontSize = 14.sp, color = TextMuted, modifier = Modifier.padding(vertical = 8.dp))
                }

                Spacer(Modifier.height(40.dp))

                Button(
                    onClick = {
                        val report = "${lang.reportLabel}: ${member.name}\n${lang.ledger}: ₹${totalSavings.toInt()}\n${lang.attendance}: $attendanceRate%"
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, report)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, null))
                    },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Sage),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(12.dp))
                    Text(lang.shareReport, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun TransactionItem(title: String, amount: String, date: Date) {
    Card(
        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, DividerColor)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
                Text(java.text.SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date), fontSize = 12.sp, color = TextMuted)
            }
            Text(
                amount,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (amount.startsWith("+")) Sage else Earth
            )
        }
    }
}

@Composable
fun ActiveLoanItem(loan: LoanRecord, lang: Language) {
    val totalInterest = (loan.principalAmount * loan.interestRate * loan.durationYears) / 100.0
    val totalPayable = loan.principalAmount + totalInterest
    val repaid = loan.repaidPrincipal + loan.repaidInterest
    val progress = (repaid / totalPayable).toFloat().coerceIn(0f, 1f)

    Card(
        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFCF7F2)),
        border = BorderStroke(1.dp, DividerColor)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(lang.totalBalance.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    Text("₹${(totalPayable - repaid).toInt()}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextDark)
                }
                Surface(color = Earth.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp)) {
                    Text(loan.status, color = Earth, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = Sage,
                trackColor = SageLight
            )
        }
    }
}

@Composable
fun SavingsScreen(vm: ShgViewModel, lang: Language) {
    val members by vm.members.collectAsState()
    val savings by vm.savings.collectAsState()
    val attendance by vm.attendance.collectAsState()
    val group by vm.group.collectAsState()
    val weeklyAmount = group?.weeklySavingsAmount ?: 100.0

    var selectedWeek by remember {
        val calendar = Calendar.getInstance()
        mutableStateOf(calendar.get(Calendar.WEEK_OF_YEAR))
    }

    val weekRange = remember(selectedWeek) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.WEEK_OF_YEAR, selectedWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        val start = calendar.time
        calendar.add(Calendar.DAY_OF_YEAR, 6)
        val end = calendar.time
        val sdf = java.text.SimpleDateFormat("MMM d", Locale.getDefault())
        "${sdf.format(start)} - ${sdf.format(end)}"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(lang.ledger, fontSize = 28.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Sage)
            Spacer(Modifier.height(16.dp))
        }

        item {
            Surface(
                color = CardWhite,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, DividerColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (selectedWeek > 1) selectedWeek-- }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.ArrowBackIos, null, modifier = Modifier.size(12.dp), tint = TextMuted)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("${lang.week.uppercase()} $selectedWeek", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Sage)
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { selectedWeek++ }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.ArrowForwardIos, null, modifier = Modifier.size(12.dp), tint = TextMuted)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(weekRange, fontSize = 11.sp, color = TextMuted)
                    }
                    Spacer(Modifier.height(16.dp))
                    val paidCount = savings.filter { it.weekNumber == selectedWeek && it.status == "Paid" }.size
                    Row {
                        Text(lang.weeklyLedger.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Spacer(Modifier.weight(1f))
                        Text("$paidCount / ${members.size} ${lang.paid}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Sage)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = if (members.isEmpty()) 0f else paidCount.toFloat() / members.size.toFloat(),
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = Sage,
                        trackColor = SoftBg
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        items(members) { member ->
            val record = savings.find { it.memberId == member.id && it.weekNumber == selectedWeek }
            val isPaid = record != null && record.status == "Paid"
            val att = attendance.find { it.memberId == member.id && it.weekNumber == selectedWeek }
            val isPresent = att?.status == "Present"

            Surface(
                color = CardWhite,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, DividerColor)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(modifier = Modifier.size(44.dp), shape = RoundedCornerShape(12.dp), color = SageLight) {
                        Box(contentAlignment = Alignment.Center) { Text(member.name[0].toString(), fontWeight = FontWeight.Bold, color = Sage) }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(member.name, fontWeight = FontWeight.Bold, color = TextDark)
                        Text(if (isPaid) "${lang.paid.uppercase()} (₹${weeklyAmount.toInt()})" else "${lang.pending.uppercase()} (₹${weeklyAmount.toInt()})", fontSize = 10.sp, fontWeight = FontWeight.Black, color = if (isPaid) Sage else Earth)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            onClick = { vm.toggleAttendance(member.id, selectedWeek) },
                            color = if (isPresent) Sage else EarthLight,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                if (isPresent) lang.present.uppercase() else lang.absent.uppercase(),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 10.sp, fontWeight = FontWeight.Black, color = if (isPresent) Color.White else TextMuted
                            )
                        }
                        Button(
                            onClick = { if (!isPaid) vm.addSavings(member.id, weeklyAmount, selectedWeek) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPaid) SageLight else Sage,
                                contentColor = if (isPaid) Sage else Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text(if (isPaid) "+ ${lang.paid}" else "₹${weeklyAmount.toInt()} ${lang.saveAction}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoansScreen(vm: ShgViewModel, lang: Language) {
    val members by vm.members.collectAsState()
    val loans by vm.loans.collectAsState()
    var showAddLoan by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(lang.loans, modifier = Modifier.weight(1f), fontSize = 28.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Sage)
                    Button(onClick = { showAddLoan = true }, colors = ButtonDefaults.buttonColors(containerColor = Sage), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text(lang.addLoan, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(lang.activeLoans.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Black, color = TextMuted, letterSpacing = 1.sp)
            }
            items(loans.filter { it.status == "Active" }) { loan ->
                val member = members.find { it.id == loan.memberId }
                LoanCardPremium(loan, member, vm, lang)
            }
        }
    }
    if (showAddLoan) {
        AddLoanDialog(vm, lang, members, onDismiss = { showAddLoan = false }, onAdd = { mid, amt, yrs ->
            vm.disburseLoan(mid, amt, yrs)
            showAddLoan = false
        })
    }
}

@Composable
fun LoanCardPremium(loan: LoanRecord, member: Member?, vm: ShgViewModel, lang: Language) {
    val totalInterest = (loan.principalAmount * loan.interestRate * loan.durationYears) / 100.0
    val totalPayable = loan.principalAmount + totalInterest
    val remainingBalance = totalPayable - (loan.repaidPrincipal + loan.repaidInterest)

    val isRecentlyUpdated = System.currentTimeMillis() - loan.updatedAt < 5000

    val statusText = if (remainingBalance <= 0) lang.closed.uppercase() else lang.active.uppercase()
    val statusColor = if (remainingBalance <= 0) Sage else Earth

    Surface(
        color = CardWhite,
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, DividerColor),
        shadowElevation = if (isRecentlyUpdated) 8.dp else 2.dp,
        modifier = Modifier.animateContentSize()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(member?.name ?: "Unknown", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextDark)
                    val dateStr = java.text.SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(loan.startDate))
                    Text("${lang.issuedLabel}: $dateStr", fontSize = 12.sp, color = TextMuted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(lang.balance.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Black, color = TextMuted)
                    Text("₹${remainingBalance.toInt()}", fontSize = 22.sp, fontFamily = FontFamily.Serif, color = Earth, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = SoftBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(lang.weeklyEMI.uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Black, color = TextMuted)
                        Text("₹${loan.weeklyEmi.toInt()}", fontWeight = FontWeight.Bold, color = Earth)
                    }
                }
                Surface(color = statusColor.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(lang.statusLabel.uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Black, color = TextMuted)
                        Text(statusText, fontWeight = FontWeight.Bold, color = statusColor)
                    }
                }
                Column(modifier = Modifier.weight(1.5f), horizontalAlignment = Alignment.End) {
                    Text("${lang.totalInterestAccrued.uppercase()}: ₹${totalInterest.toInt()}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    Text("${lang.totalPayableLabel.uppercase()}: ₹${totalPayable.toInt()}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    Text("${lang.totalWeeksLabel.uppercase()}: ${loan.totalWeeks}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    Spacer(Modifier.height(4.dp))
                    val pPerWeek = loan.principalAmount / loan.totalWeeks
                    val iPerWeek = totalInterest / loan.totalWeeks
                    Text("${lang.emiLabel} = ₹${pPerWeek.toInt()} (${lang.principalAbbr}) + ₹${iPerWeek.toInt()} (${lang.interestAbbr})", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Sage)
                }
            }
            Spacer(Modifier.height(24.dp))
            Row {
                Text("${lang.repaidLabel.uppercase()}: ₹${(loan.repaidPrincipal + loan.repaidInterest).toInt()}", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                Spacer(Modifier.weight(1f))
                Text("${lang.targetLabel.uppercase()}: ₹${totalPayable.toInt()}", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TextMuted)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = ((loan.repaidPrincipal + loan.repaidInterest) / totalPayable).toFloat().coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = Sage,
                trackColor = SoftBg
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { vm.repayPrincipal(loan.id, loan.weeklyEmi) },
                    enabled = remainingBalance > 0,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Sage),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Pay EMI (₹${loan.weeklyEmi.toInt()})", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AddMemberDialog(lang: Language, onDismiss: () -> Unit, onAdd: (String, String, String?) -> Unit, initialMember: Member? = null) {
    var name by remember { mutableStateOf(initialMember?.name ?: "") }
    var phone by remember { mutableStateOf(initialMember?.phone ?: "") }
    var photoUri by remember { mutableStateOf<String?>(initialMember?.photoUrl) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { photoUri = it.toString() }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            photoUri = "https://source.unsplash.com/random/200x200?portrait&sig=${System.currentTimeMillis()}"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(lang.addMember, fontWeight = FontWeight.Bold, fontSize = 24.sp, fontFamily = FontFamily.Serif, color = Sage) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    onClick = { if (photoUri == null) cameraLauncher.launch() },
                    modifier = Modifier.size(100.dp),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, DividerColor),
                    color = SoftBg
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (photoUri != null) {
                            AsyncImage(
                                model = photoUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).clickable { cameraLauncher.launch() },
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CameraAlt, null, tint = TextMuted)
                                Text("PHOTO", fontSize = 9.sp, fontWeight = FontWeight.Black, color = TextMuted)
                            }
                        }

                        // Gallery Button - Keep it separate
                        Box(modifier = Modifier.align(Alignment.BottomEnd).offset(8.dp, 8.dp)) {
                            Surface(
                                onClick = { galleryLauncher.launch("image/*") },
                                color = Earth,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.AddPhotoAlternate, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("NAME", fontSize = 10.sp, fontWeight = FontWeight.Black, color = TextMuted)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("E.g. Kamala Devi") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("PHONE", fontSize = 10.sp, fontWeight = FontWeight.Black, color = TextMuted)
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        placeholder = { Text("9876543210") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SoftBg, contentColor = TextDark)
                ) { Text(lang.cancel, fontWeight = FontWeight.Bold, fontSize = 18.sp) }

                Button(
                    onClick = { onAdd(name, phone, photoUri) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Earth)
                ) { Text(lang.save, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            }
        },
        dismissButton = null,
        shape = RoundedCornerShape(40.dp),
        containerColor = CardWhite
    )
}

@Composable
fun AddLoanDialog(vm: ShgViewModel, lang: Language, members: List<Member>, onDismiss: () -> Unit, onAdd: (String, Double, Double) -> Unit) {
    var selectedId by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var durationStr by remember { mutableStateOf("") }

    val principal = amountStr.toDoubleOrNull() ?: 0.0
    val years = durationStr.toDoubleOrNull() ?: 0.0
    val interest = vm.calculateInterest(principal, years)
    val total = principal + interest
    val emi = vm.calculateWeeklyEMI(principal, years)
    val weeks = (years * 52).toInt()

    val loans by vm.loans.collectAsState()
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(lang.addLoan, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = Sage) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(lang.members.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Black, color = TextMuted)
                Surface(
                    color = SoftBg,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DividerColor),
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                ) {
                    LazyColumn(modifier = Modifier.padding(8.dp)) {
                        items(members) { m ->
                            val hasActiveLoan = loans.any { it.memberId == m.id && it.status == "Active" }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedId = m.id }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selectedId == m.id, null, colors = RadioButtonDefaults.colors(selectedColor = Sage))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(m.name, fontSize = 14.sp, fontWeight = if (selectedId == m.id) FontWeight.Bold else FontWeight.Normal)
                                    if (hasActiveLoan) {
                                        Text(lang.active.uppercase(), fontSize = 8.sp, color = Earth, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(lang.principalLabel.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Black, color = TextMuted)
                        OutlinedTextField(
                            value = amountStr,
                            onValueChange = { amountStr = it },
                            placeholder = { Text("₹") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(lang.durationLabel.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Black, color = TextMuted)
                        OutlinedTextField(
                            value = durationStr,
                            onValueChange = { durationStr = it },
                            placeholder = { Text("YRS") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        )
                    }
                }

                if (principal > 0 && years > 0) {
                    Surface(
                        color = EarthLight.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("LOAN SUMMARY", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Earth)
                            Spacer(Modifier.height(8.dp))
                            CalculationRow(lang.principalLabel, "₹${principal.toInt()}")
                            CalculationRow(lang.interestLabel, "₹${interest.toInt()}")
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Earth.copy(alpha = 0.1f))
                            CalculationRow(lang.totalPayableLabel, "₹${total.toInt()}", isBold = true)
                            CalculationRow(lang.weeklyEMI, "₹${emi.toInt()}", isBold = true, color = Sage)
                            CalculationRow(lang.totalWeeksLabel, "$weeks Weeks")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val hasActiveLoan = loans.any { it.memberId == selectedId && it.status == "Active" }
                    if (hasActiveLoan) {
                        android.widget.Toast.makeText(context, lang.loanError, android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        onAdd(selectedId, principal, years)
                    }
                },
                enabled = selectedId.isNotEmpty() && principal > 0 && years > 0,
                colors = ButtonDefaults.buttonColors(containerColor = Earth),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text(lang.disburse, fontWeight = FontWeight.Bold) }
        },
        dismissButton = null,
        shape = RoundedCornerShape(28.dp),
        containerColor = CardWhite
    )
}

@Composable
fun CalculationRow(label: String, value: String, isBold: Boolean = false, color: Color = TextDark) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = TextMuted)
        Text(value, fontSize = 14.sp, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium, color = color)
    }
}














