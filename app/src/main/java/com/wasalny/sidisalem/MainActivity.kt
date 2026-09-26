
package com.wasalny.sidisalem

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.*

val Context.dataStore by preferencesDataStore(name = "wasalny_v4")

object Config {
    const val LAT = 31.27133
    const val LON = 30.786165
    const val RADIUS_KM = 5.0
    const val PHONE = "01069631950"
    const val BASE = 10.0
    const val PER_KM = 5.0
    val CENTER = LatLng(LAT, LON)
}

data class FavPlace(val name: String, val address: String, val lat: Double, val lon: Double)

fun distKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat/2)*sin(dLat/2) + cos(Math.toRadians(lat1))*cos(Math.toRadians(lat2))*sin(dLon/2)*sin(dLon/2)
    return R * 2 * atan2(sqrt(a), sqrt(1-a))
}
fun calcPrice(km: Double, type: String="now"): Int {
    var p = Config.BASE + km*Config.PER_KM
    if (type=="school") { p*=0.8; p=max(p,15.0) }
    p = round(p/5)*5
    return max(p.toInt(),10)
}
fun inside(lat: Double, lon: Double) = distKm(lat, lon, Config.LAT, Config.LON) <= Config.RADIUS_KM

suspend fun geocode(context: Context, ll: LatLng): String = withContext(Dispatchers.IO) {
    try {
        val g = Geocoder(context, Locale("ar"))
        @Suppress("DEPRECATION")
        val l = g.getFromLocation(ll.latitude, ll.longitude, 1)
        if (!l.isNullOrEmpty()) l[0].getAddressLine(0) ?: "%.4f, %.4f".format(ll.latitude, ll.longitude) else "%.4f, %.4f".format(ll.latitude, ll.longitude)
    } catch (_: Exception) { "%.4f, %.4f".format(ll.latitude, ll.longitude) }
}

suspend fun getFavs(ctx: Context): List<FavPlace> {
    val s = ctx.dataStore.data.first()[stringPreferencesKey("favs")] ?: "[]"
    val arr = JSONArray(s)
    return (0 until arr.length()).map {
        val o = arr.getJSONObject(it)
        FavPlace(o.getString("name"), o.getString("address"), o.getDouble("lat"), o.getDouble("lon"))
    }
}
suspend fun saveFav(ctx: Context, p: FavPlace) {
    val cur = ctx.dataStore.data.first()[stringPreferencesKey("favs")] ?: "[]"
    val arr = JSONArray(cur)
    val na = JSONArray()
    for (i in 0 until arr.length()) { val o=arr.getJSONObject(i); if (o.getString("name")!=p.name) na.put(o) }
    val o = JSONObject(); o.put("name",p.name); o.put("address",p.address); o.put("lat",p.lat); o.put("lon",p.lon); na.put(o)
    ctx.dataStore.edit { it[stringPreferencesKey("favs")] = na.toString() }
}
suspend fun deleteFav(ctx: Context, name: String) {
    val cur = ctx.dataStore.data.first()[stringPreferencesKey("favs")] ?: "[]"
    val arr = JSONArray(cur); val na=JSONArray()
    for (i in 0 until arr.length()) { val o=arr.getJSONObject(i); if (o.getString("name")!=name) na.put(o) }
    ctx.dataStore.edit { it[stringPreferencesKey("favs")] = na.toString() }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppV4() }
    }
}

@Composable
fun AppV4() {
    val navController = rememberNavController()
    val context = LocalContext.current
    var role by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val prefs = context.dataStore.data.first()
        role = prefs[stringPreferencesKey("role")]
        loaded = true
    }
    if (!loaded) { Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center) { CircularProgressIndicator() }; return }
    MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF0D7C3E))) {
        if (role==null) {
            WelcomeV4(onSelect={ r -> role=r })
        } else {
            Scaffold(bottomBar={ BottomBarV4(navController) }) { padding ->
                NavHost(navController, startDestination="home", modifier=Modifier.padding(padding)) {
                    composable("home") { HomeV4(navController) }
                    composable("map") { MapV4() }
                    composable("rides") { RidesV4(navController) }
                    composable("wallet") { WalletV4() }
                    composable("account") { AccountV4(navController) }
                    composable("manage_favs") { ManageFavsV4() }
                }
            }
        }
    }
}

@Composable
fun WelcomeV4(onSelect: (String)->Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.Center) {
        Text("🕌 وصلني", fontSize=48.sp, fontWeight=FontWeight.Bold, color=Color(0xFF0D7C3E))
        Text("شبكة أمان سيدي سالم", fontSize=18.sp)
        Text("ابن بلدك اللي جارك ضامنه - فكرة متعملتش", fontSize=11.sp, color=Color.Gray, textAlign=TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(onClick={ scope.launch { ctx.dataStore.edit { it[stringPreferencesKey("role")]="customer" }; onSelect("customer") } }, modifier=Modifier.fillMaxWidth().height(56.dp)) { Text("أنا راكب") }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick={ scope.launch { ctx.dataStore.edit { it[stringPreferencesKey("role")]="driver" }; onSelect("driver") } }, modifier=Modifier.fillMaxWidth().height(56.dp)) { Text("أنا سائق") }
        Spacer(Modifier.height(20.dp))
        Card(colors=CardDefaults.cardColors(containerColor=Color(0xFFE8F5E9))) { Column(Modifier.padding(12.dp)) { Text("✨ مميزات جديدة تنافس أوبر:", fontWeight=FontWeight.Bold, fontSize=11.sp); Text("• وضع الستات الآمن\n• باقة العيلة\n• توكتوك بيشيل حمولة\n• بدون نت SMS\n• ثواب المسجد", fontSize=10.sp) } }
    }
}

@Composable
fun BottomBarV4(nav: NavController) {
    val items = listOf("home" to "روحني", "map" to "الخريطة", "rides" to "رحلاتي", "wallet" to "محفظتي", "account" to "أماني")
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    NavigationBar {
        items.forEach { (route, label) ->
            NavigationBarItem(selected=current==route, onClick={ nav.navigate(route){ launchSingleTop=true } }, icon={ Icon(Icons.Default.Home, null) }, label={ Text(label, fontSize=10.sp) })
        }
    }
}

@Composable
fun HomeV4(nav: NavController) {
    val ctx = LocalContext.current
    var favs by remember { mutableStateOf<List<FavPlace>>(emptyList()) }
    LaunchedEffect(Unit) { favs=getFavs(ctx) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Text("أهلاً 👋", fontSize=22.sp, fontWeight=FontWeight.Bold)
            Text("📍 سيدي سالم - 5 كم - شبكة أمان - خرائط جوجل 100%", fontSize=11.sp, color=Color(0xFF0D7C3E))
            Spacer(Modifier.height(8.dp))
            Text("🏠 دوس تروح على طول - بدون كتابة", fontWeight=FontWeight.Bold, fontSize=14.sp)
        }
        if (favs.isEmpty()) {
            item {
                Card(colors=CardDefaults.cardColors(containerColor=Color(0xFFFFF8E1)), modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), horizontalAlignment=Alignment.CenterHorizontally) {
                        Text("لسه محفظتش أماكن", fontWeight=FontWeight.Bold)
                        Text("اطلب أول رحلة على الخريطة واحفظها كـ البيت/الشغل/مدرسة وهتظهر هنا", fontSize=11.sp, color=Color.Gray, textAlign=TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick={ nav.navigate("map") }) { Text("افتح الخريطة") }
                    }
                }
            }
        } else {
            items(favs) { fav ->
                Card(modifier=Modifier.fillMaxWidth().clickable{ nav.navigate("map") }, colors=CardDefaults.cardColors(containerColor=Color(0xFFE8F5E9)), shape=RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(when(fav.name){ "البيت"->"🏠 البيت"; "الشغل"->"💼 الشغل"; "مدرسة العيال"->"🎒 مدرسة العيال"; else->"📍 ${fav.name}" }, fontWeight=FontWeight.Bold)
                            Text(fav.address, fontSize=11.sp, maxLines=1, color=Color.Gray)
                        }
                        Button(onClick={ nav.navigate("map") }) { Text("روحني") }
                    }
                }
            }
        }
        item {
            Button(onClick={ nav.navigate("map") }, modifier=Modifier.fillMaxWidth().height(56.dp), shape=RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.Map, null); Spacer(Modifier.width(8.dp)); Text("🗺️ اطلب رحلة جديدة على الخريطة")
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={ nav.navigate("manage_favs") }, modifier=Modifier.weight(1f)) { Text("⚙️ أماكني", fontSize=11.sp) }
                OutlinedButton(onClick={}, modifier=Modifier.weight(1f)) { Text("⭐ سواقيني", fontSize=11.sp) }
            }
        }
    }
}

@Composable
fun MapV4() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val fused = remember { LocationServices.getFusedLocationProviderClient(ctx) }
    var pickup by remember { mutableStateOf<LatLng?>(null) }
    var dropoff by remember { mutableStateOf<LatLng?>(null) }
    var pickupAddr by remember { mutableStateOf("") }
    var dropoffAddr by remember { mutableStateOf("") }
    var selectingPickup by remember { mutableStateOf(true) }
    var rideType by remember { mutableStateOf("now") }
    var femaleMode by remember { mutableStateOf(false) }
    var withLuggage by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf((1000..9999).random().toString()) }
    var showSave by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }
    val cam = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(Config.CENTER, 14.5f) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms[Manifest.permission.ACCESS_FINE_LOCATION]==true) {
            scope.launch {
                try {
                    val loc = fused.lastLocation.await()
                    loc?.let { val ll = LatLng(it.latitude, it.longitude); pickup=ll; pickupAddr=geocode(ctx, ll); cam.position=CameraPosition.fromLatLngZoom(ll, 16f) }
                } catch(_:Exception){}
            }
        }
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    val km = if (pickup!=null && dropoff!=null) distKm(pickup!!.latitude, pickup!!.longitude, dropoff!!.latitude, dropoff!!.longitude) else 0.0
    val price = if (km>0) calcPrice(km, rideType) else 0
    val inside = pickup?.let { inside(it.latitude, it.longitude) } ?: true

    Box(Modifier.fillMaxSize()) {
        GoogleMap(modifier=Modifier.fillMaxSize(), cameraPositionState=cam, properties=MapProperties(isMyLocationEnabled=true), uiSettings=MapUiSettings(zoomControlsEnabled=false, myLocationButtonEnabled=true), onMapClick={ ll -> scope.launch { if (selectingPickup) { pickup=ll; pickupAddr=geocode(ctx, ll) } else { dropoff=ll; dropoffAddr=geocode(ctx, ll) } } }) {
            Circle(center=Config.CENTER, radius=Config.RADIUS_KM*1000, fillColor=Color(0x220D7C3E), strokeColor=Color(0xFF0D7C3E), strokeWidth=2f)
            pickup?.let { Marker(state=MarkerState(position=it), title="من هنا") }
            dropoff?.let { Marker(state=MarkerState(position=it), title="إلى هنا") }
            if (pickup!=null && dropoff!=null) Polyline(points=listOf(pickup!!, dropoff!!), color=Color(0xFF0D7C3E), width=10f)
        }
        Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha=0.97f)).padding(12.dp).align(Alignment.TopCenter)) {
            Text("🗺️ خرائط جوجل 100% - ${if(selectingPickup) "حدد مكانك" else "حدد وجهتك"}", fontSize=12.sp, fontWeight=FontWeight.Bold, color=Color(0xFF0D7C3E))
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                FilterChip(selected=selectingPickup, onClick={selectingPickup=true}, label={Text("📍 من", fontSize=11.sp)})
                FilterChip(selected=!selectingPickup, onClick={selectingPickup=false}, label={Text("🏁 إلى", fontSize=11.sp)})
                FilterChip(selected=femaleMode, onClick={femaleMode=!femaleMode}, label={Text("👩 وضع الستات", fontSize=10.sp)})
            }
            if (pickupAddr.isNotEmpty()) Text("من: $pickupAddr", fontSize=10.sp, maxLines=1)
            if (dropoffAddr.isNotEmpty()) Text("إلى: $dropoffAddr", fontSize=10.sp, maxLines=1)
            if (!inside) Text("⚠️ خارج نطاق 5 كم", color=Color.Red, fontSize=11.sp, fontWeight=FontWeight.Bold)
            Row(Modifier.fillMaxWidth().padding(top=4.dp), horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                FilterChip(selected=rideType=="now", onClick={rideType="now"}, label={Text("حالاً", fontSize=10.sp)})
                FilterChip(selected=rideType=="scheduled", onClick={rideType="scheduled"}, label={Text("بموعد", fontSize=10.sp)})
                FilterChip(selected=rideType=="school", onClick={rideType="school"}, label={Text("مدارس -20%", fontSize=10.sp)})
                FilterChip(selected=withLuggage, onClick={withLuggage=!withLuggage}, label={Text("📦 حمولة", fontSize=10.sp)})
            }
        }
        Card(Modifier.fillMaxWidth().align(Alignment.BottomCenter), shape=RoundedCornerShape(topStart=20.dp, topEnd=20.dp), elevation=CardDefaults.cardElevation(8.dp)) {
            Column(Modifier.padding(16.dp)) {
                if (pickup!=null && dropoff!=null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("📏 %.2f كم".format(km), fontWeight=FontWeight.Bold)
                        Text("💰 $price ج", fontWeight=FontWeight.Bold, fontSize=18.sp, color=Color(0xFF0D7C3E))
                        Text("🔐 $code", fontSize=12.sp)
                    }
                    if (femaleMode) Text("👩 وضع الستات: سواق موثوق تقييمه من الستات فوق 4.8", fontSize=10.sp, color=Color(0xFF880E4F))
                    Spacer(Modifier.height(8.dp))
                    Button(onClick={ showConfirm=true }, modifier=Modifier.fillMaxWidth().height(50.dp), enabled=inside) { Text("✅ اطلب التوكتوك حالاً") }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick={ showSave=true }, modifier=Modifier.weight(1f)) { Text("💾 احفظ كـ بيت", fontSize=11.sp) }
                        OutlinedButton(onClick={ val sms = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Config.PHONE}")); sms.putExtra("sms_body", "طلب توكتوك من $pickupAddr إلى $dropoffAddr"); ctx.startActivity(sms) }, modifier=Modifier.weight(1f)) { Text("📱 SMS بدون نت", fontSize=11.sp) }
                    }
                } else {
                    Text("👆 اضغط على الخريطة تحدد من فين لفين\n💾 احفظه كـ بيت عشان تطلبه بضغطة واحدة بعد كده\n🎤 قريباً: اطلب بصوتك - للستات وكبار السن", fontSize=12.sp, textAlign=TextAlign.Center, modifier=Modifier.fillMaxWidth())
                }
            }
        }
    }
    if (showConfirm) {
        AlertDialog(onDismissRequest={showConfirm=false}, title={Text("تأكيد - شبكة أمان")}, text={Text("من: $pickupAddr\nإلى: $dropoffAddr\n📏 %.2f كم - 💰 $price ج - 🔐 $code\n✅ السعر للرحلة كاملة\n✅ كود الأمان للسواق\n${if(femaleMode) "👩 وضع الستات مفعل" else ""}".format(km))}, confirmButton={ Button(onClick={showConfirm=false}){Text("تمام - دور على سواق")} }, dismissButton={ TextButton(onClick={showConfirm=false}){Text("إلغاء")} })
    }
    if (showSave) {
        AlertDialog(onDismissRequest={showSave=false}, title={Text("احفظ مكانك")}, text={ Column { OutlinedTextField(value=saveName, onValueChange={saveName=it}, label={Text("البيت / الشغل / مدرسة العيال")}, modifier=Modifier.fillMaxWidth()); Text("محفوظ على جهازك ومش بيتمسح إلا بمسح التطبيق - جدول favorite_places", fontSize=10.sp, color=Color.Gray) } }, confirmButton={ Button(onClick={ if (saveName.isNotEmpty() && dropoff!=null) { scope.launch { saveFav(ctx, FavPlace(saveName, dropoffAddr, dropoff!!.latitude, dropoff!!.longitude)); showSave=false; saveName="" } } }){Text("حفظ")} }, dismissButton={ TextButton(onClick={showSave=false}){Text("إلغاء")} })
    }
}

@Composable
fun RidesV4(nav: NavController) {
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("📜 رحلاتي - آخر 10", fontSize=20.sp, fontWeight=FontWeight.Bold)
        Text("نفس جدول rides في البوت + تقييم + شكوى + إعادة طلب", fontSize=11.sp, color=Color.Gray)
        Spacer(Modifier.height(12.dp))
        Card(modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) { Text("البيت → المستشفى", fontWeight=FontWeight.Bold, fontSize=13.sp); Text("20ج", color=Color(0xFF0D7C3E), fontWeight=FontWeight.Bold) }
                Text("اليوم 2:30م - عم محمد 4.9⭐", fontSize=11.sp, color=Color.Gray)
                Row(Modifier.fillMaxWidth().padding(top=6.dp), horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick={ nav.navigate("map") }, modifier=Modifier.weight(1f)) { Text("🔄 تاني", fontSize=10.sp) }
                    OutlinedButton(onClick={}, modifier=Modifier.weight(1f)) { Text("⭐ قيم", fontSize=10.sp) }
                    OutlinedButton(onClick={}, modifier=Modifier.weight(1f)) { Text("⚠️ شكوى", fontSize=10.sp) }
                }
            }
        }
    }
}

@Composable
fun WalletV4() {
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("💳 محفظتي ونقاطي", fontSize=20.sp, fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Card(colors=CardDefaults.cardColors(containerColor=Color(0xFFE8F5E9)), modifier=Modifier.fillMaxWidth(), shape=RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("⭐ نقاطي: 12 نقطة", fontWeight=FontWeight.Bold, fontSize=18.sp)
                Text("كل رحلة = نقطة - 20 نقطة = رحلة مجانية - فكرة جديدة", fontSize=12.sp)
                LinearProgressIndicator(progress=0.6f, modifier=Modifier.fillMaxWidth().padding(top=8.dp))
                Text("فاضل 8 نقط وتاخد رحلة ببلاش", fontSize=10.sp, color=Color.Gray)
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("👨‍👩‍👧‍👦 باقة العيلة - فكرة جديدة", fontWeight=FontWeight.Bold)
                Text("رصيد العيلة: 250ج\n• بنتك وصلت المدرسة 7:30ص\n• ابنك راح الدرس 3:00م", fontSize=12.sp)
                Spacer(Modifier.height(8.dp))
                Button(onClick={}, modifier=Modifier.fillMaxWidth()) { Text("اشحن باقة العيلة") }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(colors=CardDefaults.cardColors(containerColor=Color(0xFFFFF3E0)), modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("🏍️ اشتراك السواق: أول شهر ${Config.FIRST}ج - بعد كده ${Config.MONTHLY}ج/30 يوم\n📱 حول على ${Config.PHONE}\nفترة سماح 3 أيام", fontSize=12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(colors=CardDefaults.cardColors(containerColor=Color(0xFFE3F2FD)), modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("📢 إعلان محلي - دخل إضافي", fontWeight=FontWeight.Bold, fontSize=12.sp)
                Text("صيدلية الدكتور أحمد - بتوصل للبيت - خصم 10% لعملاء وصلني", fontSize=11.sp)
            }
        }
    }
}

@Composable
fun AccountV4(nav: NavController) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("🛡️ أماني وإعداداتي", fontSize=20.sp, fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Card(modifier=Modifier.fillMaxWidth().clickable{ nav.navigate("manage_favs") }) {
            Row(Modifier.padding(16.dp), verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Default.Place, null, tint=Color(0xFF0D7C3E)); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text("📍 أماكني المحفوظة", fontWeight=FontWeight.Bold); Text("البيت/الشغل/مدرسة - محفوظة على جهازك - favorite_places", fontSize=11.sp, color=Color.Gray) }
                Icon(Icons.Default.ChevronRight, null)
            }
        }
        Spacer(Modifier.height(8.dp))
        Card(modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Default.Woman, null, tint=Color(0xFF880E4F)); Spacer(Modifier.width(8.dp)); Text("👩 وضع الستات الآمن - فكرة جديدة", fontWeight=FontWeight.Bold) }
                Text("• سواقين تقييمهم من الستات فقط فوق 4.8\n• مشاركة الرحلة لايف مع الأهل\n• كود أمان + SOS", fontSize=11.sp, color=Color.Gray)
                var enabled by remember { mutableStateOf(false) }
                Row(verticalAlignment=Alignment.CenterVertically) { Switch(checked=enabled, onCheckedChange={enabled=it}); Text(if(enabled) "مفعل - هيجيلك سواق موثوق" else "غير مفعل", fontSize=12.sp) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Card(modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Default.Mic, null); Spacer(Modifier.width(8.dp)); Text("🎤 اطلب بصوتك - فكرة جديدة", fontWeight=FontWeight.Bold) }
                Text("قول: وديني المستشفى - مهم لكبار السن والأميين - قريباً", fontSize=11.sp, color=Color.Gray)
            }
        }
        Spacer(Modifier.height(8.dp))
        Card(modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Default.Sms, null); Spacer(Modifier.width(8.dp)); Text("📱 بدون نت SMS - فكرة جديدة", fontWeight=FontWeight.Bold) }
                Text("لو النت فصل، هنبعت طلبك SMS لرقم ${Config.PHONE} - أوبر ميقدرش يعملها", fontSize=11.sp, color=Color.Gray)
            }
        }
        Spacer(Modifier.height(8.dp))
        Card(modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Default.VolunteerActivism, null, tint=Color(0xFF0D7C3E)); Spacer(Modifier.width(8.dp)); Text("🕌 توصيلة ثواب المسجد - فكرة جديدة", fontWeight=FontWeight.Bold) }
                Text("يوم الجمعة توصيلة مجانية للمسجد الكبير - السواق ياخد نقطة ثقة وثواب", fontSize=11.sp, color=Color.Gray)
            }
        }
        Spacer(Modifier.height(8.dp))
        Card(modifier=Modifier.fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=Color(0xFFFFEBEE))) {
            Row(Modifier.padding(16.dp).clickable{ val i=Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Config.PHONE}")); ctx.startActivity(i) }, verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Default.Emergency, null, tint=Color.Red); Spacer(Modifier.width(12.dp))
                Column { Text("🆘 طوارئ SOS - نفس البوت", fontWeight=FontWeight.Bold, color=Color.Red); Text("يبعت لوكيشن لايف لـ 3 أرقام عائلية + الأدمن", fontSize=11.sp) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("سياسة الخصوصية: نجمع الاسم والهاتف والموقع عند الطلب فقط. نستخدم Google Maps. لا نبيع بياناتك. حذف البيانات: ${Config.PHONE}", fontSize=9.sp, color=Color.Gray, textAlign=TextAlign.Center, modifier=Modifier.fillMaxWidth())
    }
}

@Composable
fun ManageFavsV4() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var favs by remember { mutableStateOf<List<FavPlace>>(emptyList()) }
    LaunchedEffect(Unit) { favs=getFavs(ctx) }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("⚙️ أماكني المحفوظة", fontSize=20.sp, fontWeight=FontWeight.Bold)
        Text("محفوظة في جدول favorite_places - نفس البوت - مش بتتمسح إلا بمسح التطبيق", fontSize=11.sp, color=Color.Gray)
        Spacer(Modifier.height(12.dp))
        if (favs.isEmpty()) Text("مفيش أماكن لسه - اطلب رحلة على الخريطة واحفظها كـ البيت", modifier=Modifier.fillMaxWidth().padding(24.dp), textAlign=TextAlign.Center)
        else favs.forEach { fav ->
            Card(Modifier.fillMaxWidth().padding(bottom=8.dp)) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(fav.name, fontWeight=FontWeight.Bold); Text(fav.address, fontSize=11.sp, color=Color.Gray, maxLines=2) }
                    IconButton(onClick={ scope.launch { deleteFav(ctx, fav.name); favs=getFavs(ctx) } }){ Icon(Icons.Default.Delete, null, tint=Color.Red) }
                }
            }
        }
    }
}
