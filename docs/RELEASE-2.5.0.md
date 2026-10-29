Paper **26.2**, Java **25** uchun bitta SentinelAC JAR. Boshqa Paper versiyalari va Folia ushbu release qamroviga kirmaydi.

O‘rnatish: server/config/database nusxasini oling, eski Sentinel va alohida GrimAC JARlarini chiqarib, `SentinelAC-2.5.0-paper26.2.jar`ni `plugins/`ga qo‘ying va restart qiling. Buyruqlar: `/sac`, `/sentinel`, `/sentinelac`. Konfiguratsiya `plugins/SentinelAC/`da.

Yadro va API pin qilingan manbalardan yig‘iladi. Kanal blacklisti, crystal/anchor limitlari, chegaralangan diagnostika va webhook navbati, lokal dump fayllari mavjud. Asl copyright/GPL yozuvlari saqlangan; GrimAC/GrimAPI jamoasiga rahmat.

Tekshiruvlar: 76 avtomatik test, JAR tuzilishi/branding tekshiruvi, Paper 26.2 build 135 startup/reload/buyruqlari, SQLite va lokal dump. ViaVersion/ViaBackwards 5.12.2 bilan 1.21.11/26.1 ulanishlari, blacklist kick va Simulation setback sinovlari o‘tgan.

Avval staging serverda sinang. To‘liq native 26.2 o‘yin sessiyalari, katta online, barcha Via vehicle kombinatsiyalari va tashqi database backendlar hali to‘liq tekshirilmagan. Kanal tekshiruvi barcha yashirin modlarni ko‘ra olmaydi. Nol lag yoki barcha cheatlarni aniqlash kafolati berilmaydi. Batafsil: [validatsiya](https://github.com/dilmuratovv/SentinelAC/blob/main/docs/VALIDATION.md).

Manba (submodule’lar bilan):
```sh
git clone --branch v2.5.0-paper26.2 --recurse-submodules https://github.com/dilmuratovv/SentinelAC.git
```

SHA256: `744a4d5d19722f228be9a90ac18f9bd626f0c98fbffd10100fc2071aef29bcfb`. Yuklamadagi `SHA256SUMS.txt` bilan tekshiring.
