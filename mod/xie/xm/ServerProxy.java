package mod.xie.xm;

import java.io.File;
import java.io.InputStream;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.src.ModLoader;

public class ServerProxy extends XieProxy {

	@Override
	public String getMinecraftPath() {
		return MinecraftServer.getServer().getFile(".").getPath();
	}

	@Override
	public boolean validateImageFile(String s) {
		File f = new File(s);
		return f.exists() && f.isFile();
	}

}
