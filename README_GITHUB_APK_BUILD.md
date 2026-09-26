# 🕌 وصلني - سيدي سالم - Build APK على GitHub

## ازاي تطلع APK بضغطة واحدة (بدون Android Studio):

### الخطوة 1: اعمل Repo على GitHub
1- ادخل https://github.com/new
2- اسم الريبو: wasalny-v4
3- خليه Public
4- دوس Create

### الخطوة 2: ارفع الكود ده
- فك ضغط Wasalny-V4-GitHub-Ready.zip
- ارفع كل الملفات للـ Repo (drag & drop)

### الخطوة 3: حط Google Maps API Key (مهم للخريطة)
1- في GitHub Repo -> Settings -> Secrets and variables -> Actions -> New repository secret
2- Name: MAPS_API_KEY
3- Value: حط الـ Key بتاعك من https://console.cloud.google.com/apis/credentials
   - اعمل Enable: Maps SDK for Android + Geocoding API

### الخطوة 4: الـ APK هيتبني لوحده
- أول ما ترفع الكود، GitHub هيبدأ Build
- روح تبويب Actions -> هتلاقي Build شغال
- بعد 3-4 دقايق هيخلص
- دوس على الـ Run -> في الآخر هتلاقي Artifacts -> wasalny-v4-apk -> حمله
- ده الـ APK جاهز للتثبيت

### الخطوة 5: لينك تحميل مباشر
- بعد ما الـ Build يخلص، هيعمل Release تلقائي
- روح تبويب Releases -> هتلاقي APK جاهز بلينك تحميل مباشر تقدر تبعته لأي حد على الواتساب

## لو مش عايز تحط Maps Key:
الـ APK هيتبني برضه بس الخريطة هتظهر رمادي - باقي التطبيق شغال 100%

## حجم الـ APK: حوالي 15 ميجا - شغال على أندرويد 7 لفوق