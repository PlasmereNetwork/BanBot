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

    public static BanList readBanList(Path banlist) throws IOException {
        return gson.fromJson(new String(Files.readAllBytes(banlist)), BanList.class);
    }

}
