package mod.xie.xm.util;

import java.util.HashMap;

import mod.xie.xm.blocks.XieBlockPlant;
import net.minecraft.world.World;


public class BonemealHandler {

	private HashMap<Integer,XieBlockPlant> map;
	private static BonemealHandler instance;
	
	private BonemealHandler() {
		map = new HashMap<Integer,XieBlockPlant>();
	}
	
	public static void add(XieBlockPlant b) {
		if (instance==null) instance = new BonemealHandler();
		instance.map.put(b.blockID,b);
	}

	public static boolean doBonemeal(World world, int blockID, int X, int Y, int Z) {
		if (instance==null) instance = new BonemealHandler();
		return instance.doBonemeal(world, blockID, X, Y, Z);
	}
	
	private boolean attemptBonemealing(World world, int blockID, int X, int Y, int Z) {
		if (map.containsKey(blockID)) {
			if (map.get(blockID).fertilisable) {
				map.get(blockID).fertilize(world, X, Y, Z);
				return true;
			}
		}
		return false;
	}
}