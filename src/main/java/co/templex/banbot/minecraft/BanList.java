package co.templex.banbot.minecraft;

import lombok.Value;

import java.util.ArrayList;

public class BanList extends ArrayList<BanList.BanListEntry> {

    @Value
    public class BanListEntry {
        String uuid, name, created, source, expires, reason;
    }

}
