# HubNotification - Car HUD Android Companion

Android app nhan notification (Google Maps, cuoc goi, tin nhan), lay speed/clock/battery, dong goi JSON va gui qua BLE GATT den ESP32 HUD.

## 1) Muc tieu he thong

- Ket noi toi ESP32 co ten CarHUD-ESP32.
- Gui JSON theo schema thong nhat voi firmware docsFw/main.cpp.
- Duy tri chay nen bang Foreground Service.
- Cho phep bat/tat tung app nguon notification bang man hinh quan ly va luu vao SharedPreferences.
- Cho phep doi HUD/normal mode, flip va brightness khi BLE da ready.
- Danh sach app notification duoc load tu tat ca app da cai tren may.
- Man hinh app list co tim kiem + filter de xu ly tot khi co nhieu app.
- Co man hinh debug log de xem raw notification title/text/subText ngay tren app.

## 2) Kien truc chinh

- MainActivity
- NotificationAppSettingsActivity
- CarHudService (foreground)
- CarHudNotificationListener (NotificationListenerService)
- BleClient (scan/connect/mtu/write queue/reconnect)
- GoogleMapsNavParser (parse nav thong bao Maps)
- CarHudBus (encode JSON va gui)
- NotificationAppConfig (SharedPreferences cho app filtering)
- CarHudDisplayConfig (SharedPreferences cho HUD/normal, flip, brightness)

## 3) Giao thuc BLE

- Device name: CarHUD-ESP32
- Service UUID: 6e400001-b5a3-f393-e0a9-e50e24dcca9e
- RX (Android -> ESP32): 6e400002-b5a3-f393-e0a9-e50e24dcca9e
- TX (ESP32 -> Android): 6e400003-b5a3-f393-e0a9-e50e24dcca9e
- MTU muc tieu: 247

JSON discriminator field la t, gom cac loai:

- nav: {"t":"nav","arr":"right","d":200,"u":"m","s":"Le Loi"}
- spd: {"t":"spd","v":42}
- call: {"t":"call","n":"Mom","p":"+8490..."}
- sms: {"t":"sms","f":"Anh","m":"Em o dau"}
- clk: {"t":"clk","h":14,"m":30}
- bat: {"t":"bat","p":75}
- cfg: {"t":"cfg","mode":"hud","flip":"v","br":255,"save":true}
- clr: {"t":"clr"}

## 4) Luong chay runtime

1. User cap runtime permissions + Notification Access.
2. Start HUD Service tu MainActivity.
3. CarHudService khoi tao BleClient, request location updates, push clock/battery dinh ky.
4. CarHudNotificationListener nhan notification he thong.
5. Listener parse du lieu va goi CarHudBus publish JSON.
6. BleClient queue payload va ghi vao RX characteristic.
7. BleClient luu MAC ESP32 vao SharedPreferences sau khi connect thanh cong va uu tien reconnect bang MAC da luu.
8. Neu da tung BLE ready roi bi disconnect qua 30s, CarHudService tu stop de tat scan/location foreground va tranh ton pin.

## 5) Man hinh quan ly app notification

- Man hinh: NotificationAppSettingsActivity.
- Nguon du lieu app list: NotificationAppConfig.
- Trang thai bat/tat luu theo packageName trong SharedPreferences file car_hud_notification_apps.
- Listener chi xu ly notification neu package dang duoc bat.
- Danh sach app duoc lay bang PackageManager (tat ca app da cai trong may).
- Ho tro tim kiem theo ten app/package.
- Ho tro filter: Tat ca / Da bat / Da tat.

Man hinh debug:

- NotificationDebugLogActivity hien thi log raw title/text/subText.
- Co nut Refresh/Clear de theo doi parser va notification routing nhanh.

Mac dinh da bat cho nhom app pho bien:

- com.google.android.apps.maps
- com.google.android.dialer
- com.android.phone
- com.samsung.android.dialer
- com.google.android.apps.messaging
- org.telegram.messenger
- com.zing.zalo
- com.facebook.orca
- com.whatsapp

Tat ca app con lai mac dinh tat va co the bat trong man hinh quan ly.

## 6) Google Maps parsing

GoogleMapsNavParser da ho tro:

- Action tieng Anh: turn left/right, slight, sharp, u-turn, continue.
- Action tieng Viet: re trai/phai, quay dau, di thang, tiep tuc (co ca dang co dau va khong dau).
- Distance pattern rong hon: In 200 m, Sau 200 m, 0.5 km, 500 ft, ...
- Khoang cach decimal km duoc doi sang met truoc khi gui firmware, vi firmware v3 format lai thanh 1 chu so thap phan.
- Lay text tu nhieu truong notification: EXTRA_TEXT, EXTRA_BIG_TEXT, EXTRA_TEXT_LINES.

Neu parse duoc action + street nhung khong ro distance, he thong van gui nav voi distance = 0 de HUD cap nhat huong re.

## 7) Permissions quan trong

Trong manifest:

- Bluetooth: BLUETOOTH_SCAN, BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE
- Location: ACCESS_FINE_LOCATION
- Foreground service: FOREGROUND_SERVICE, FOREGROUND_SERVICE_CONNECTED_DEVICE, FOREGROUND_SERVICE_LOCATION
- Notification access bind: BIND_NOTIFICATION_LISTENER_SERVICE
- Runtime khac: POST_NOTIFICATIONS, READ_PHONE_STATE, READ_CONTACTS

## 8) Build va run

Build debug:

./gradlew :app:assembleDebug

## 9) File map nhanh

- app/src/main/java/com/hieptran/hubnotification/MainActivity.java
- app/src/main/java/com/hieptran/hubnotification/NotificationAppSettingsActivity.java
- app/src/main/java/com/hieptran/hubnotification/NotificationAppConfig.java
- app/src/main/java/com/hieptran/hubnotification/CarHudService.java
- app/src/main/java/com/hieptran/hubnotification/CarHudNotificationListener.java
- app/src/main/java/com/hieptran/hubnotification/BleClient.java
- app/src/main/java/com/hieptran/hubnotification/GoogleMapsNavParser.java
- app/src/main/java/com/hieptran/hubnotification/CarHudBus.java
- docsFw/main.cpp
- docsFw/Android tech spec.md

## 10) Huong mo rong tiep theo

- Them parser unit tests cho 20+ mau notification Maps thuc te.
- Tach call-state tu TelephonyManager de phan biet incoming/ringing/end ro hon.
- Them export log ra file de de chia se bug report.
