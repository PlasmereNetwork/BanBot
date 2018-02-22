package co.templex.banbot.minecraft;

import lombok.Value;

import java.util.ArrayList;

public class UserCache extends ArrayList<UserCache.UserCacheEntry> {

    @Value
    public class UserCacheEntry {
        String name, uuid, expiresOn;
    }

}
