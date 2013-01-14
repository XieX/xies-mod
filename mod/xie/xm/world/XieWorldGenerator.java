package mod.xie.xm.world;

import java.util.Random;

import mod.xie.xm.util.Spawnable;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;


import cpw.mods.fml.common.IWorldGenerator;

public class XieWorldGenerator implements IWorldGenerator {

	private static XieWorldGenerator instance = new XieWorldGenerator();
	
	private XieWorldGenerator() {}
	
	public static XieWorldGenerator get() {
		return instance;
	}
	
	@Override
	public void generate(Random random, int chunkX, int chunkZ, World world, IChunkProvider chunkGenerator, IChunkProvider chunkProvider) {
		if (world.getBiomeGenForCoords(chunkX, chunkZ).equals("Hell"))
			Spawnable.generateNether(world, random, chunkX*16 + random.nextInt(16), chunkZ*16 + random.nextInt(16));
		else
			Spawnable.generateSurface(world, random, chunkX*16 + random.nextInt(16), chunkZ*16 + random.nextInt(16));
	}

}
