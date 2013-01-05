package xie.mods.xm;

import net.minecraft.world.World;

public abstract class XieProxy {

	public abstract String getMinecraftPath();
	public abstract boolean validateImageFile(String file);
	
	public int addOverride(String type, String path) { return 0; }
	public void preloadTextures() {}
	public void addBlockDestroyEffects(int i, int j, int k, int bid, int l) {}
	public void playSoundEffect(World world, double i, double j, double k, String sound, float volume, float pitch) {}
	public void playAuxSFX(World world, int i, int j, int k, int id, int n) {}

}
