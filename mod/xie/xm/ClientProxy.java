package mod.xie.xm;

import java.io.InputStream;


import mod.xie.xm.file.SpritesheetManager;
import net.minecraft.client.Minecraft;
import net.minecraft.src.ModLoader;
import net.minecraft.world.World;

public class ClientProxy extends XieProxy {

	@Override
	public String getMinecraftPath() {
		return ".";
	}
	
	@Override
	@Deprecated
	public boolean validateImageFile(String s) {
		return true;
	}

	@Override
	@Deprecated
	public int addOverride(String type, String path) {
		return ModLoader.addOverride(type,path);
	}

	@Override
	public void addBlockDestroyEffects(int i, int j, int k, int bid, int l) {
		ModLoader.getMinecraftInstance().effectRenderer.addBlockDestroyEffects(i, j, k, bid, l);		
	}

	@Override
	public void playSoundEffect(World world, double i, double j, double k, String sound, float volume, float pitch) {
		world.playSoundEffect(i, j, k, sound, volume, pitch);
	}
	
	@Override
	public void playAuxSFX(World world, int i, int j, int k, int id, int n) {
		world.playAuxSFX(i, j, k, id, n);
	}

}
