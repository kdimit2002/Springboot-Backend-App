package com.example.webapp.BidNow.Enums;



//Todo: Use Cloudflare CDN


public enum Avatar {
    BEARD_MAN_AVATAR("https://pub-6cd4fca3122d4e93bf79326e6762f99e.r2.dev/images/Avatars/BEARD_MAN_AVATAR.png"),
    MAN_AVATAR("https://pub-6cd4fca3122d4e93bf79326e6762f99e.r2.dev/images/Avatars/MAN_AVATAR.png"),
    BLONDE_GIRL_AVATAR("https://pub-6cd4fca3122d4e93bf79326e6762f99e.r2.dev/images/Avatars/BLONDE_GIRL_AVATAR.png"),
    GIRL_AVATAR("https://pub-6cd4fca3122d4e93bf79326e6762f99e.r2.dev/images/Avatars/GIRL_AVATAR.png"),
    DEFAULT_AVATAR("https://pub-6cd4fca3122d4e93bf79326e6762f99e.r2.dev/images/Avatars/DEFAULT_AVATAR.png"),
    DEFAULT("https://pub-6cd4fca3122d4e93bf79326e6762f99e.r2.dev/images/Avatars/DEFAULT_AVATAR.png");


    private final String url;

    Avatar(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }
}
