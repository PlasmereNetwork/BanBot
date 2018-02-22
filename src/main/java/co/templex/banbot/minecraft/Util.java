package co.templex.banbot.minecraft;

import com.google.gson.Gson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Util {

    private static final Gson gson = new Gson();

    private Util() {
        throw new UnsupportedOperationException("Instantiation not permitted.");
    }

    public static String readPathAsString(Path banlist) throws IOException {
        return new String(Files.readAllBytes(banlist));
    }

    public static BanList readBanList(Path banlist) throws IOException {
        return gson.fromJson(readPathAsString(banlist), BanList.class);
    }

    public static UserCache readUserCache(Path usercache) throws IOException {
        return gson.fromJson(readPathAsString(usercache), UserCache.class);
    }

}
