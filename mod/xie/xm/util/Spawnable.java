package mod.xie.xm.util;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import mod.xie.xm.Work;
import mod.xie.xm.blocks.XieBlockLeaves;
import mod.xie.xm.file.XieLog;
import net.minecraft.block.Block;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;


public class Spawnable {
	public static List<Spawnable> surface = new LinkedList<Spawnable>();
	public static List<Spawnable> nether = new LinkedList<Spawnable>();
	public static List<Spawnable> dungeon = new LinkedList<Spawnable>();
	public static List<Spawnable> ore = new LinkedList<Spawnable>();
	
	public Block spawns;
	public int meta;
	public int metaFactors[];

	public String biomes[] = null;
	public List<Integer> spawnsOn = new LinkedList<Integer>();
	public List<Integer> spawnsIn = new LinkedList<Integer>();
	public List<Integer> spawnsUnder = new LinkedList<Integer>();
	public List<Integer> spawnsNear = new LinkedList<Integer>();
	public byte clusterMetas[];
	public int nearDistance = 6;
	public int spawnFactor = 15;
	public int yMax = -1;
	public int yMin = -1;
	public int clusterSizeMax = 1;
	public boolean clump = false;
	
	public Spawnable(Block block, int meta) {
		spawns = block;
		this.meta = meta;
	}
	
	public Spawnable(Block block) { 
		spawns = block;
		meta = 0;
	}
	
	protected boolean canSpawnHere(World world, int i, int j, int k) {
		if (spawns == null) return false;
		
		if (spawnsNear!=null && !spawnsNear.isEmpty()) {
			boolean foundSome = false;
			for (int blockID : spawnsNear) {
				for (int dx=-nearDistance; dx<=nearDistance && !foundSome; dx++)
					for (int dy=-nearDistance; dx<=nearDistance && !foundSome; dx++)
						for (int dz=-nearDistance; dx<=nearDistance && !foundSome; dx++) {
							if (world.getBlockId(i+dx, j+dy, k+dz)==blockID) foundSome=true;
						}
				if (foundSome) continue;
			}
			if (!foundSome) return false;
		}
		
		boolean canSpawnOn = spawnsOn.isEmpty() || spawnsOn.contains(world.getBlockId(i, j-1, k));
		boolean canSpawnIn = spawnsIn.isEmpty() || spawnsIn.contains(world.getBlockId(i, j, k));
		boolean canSpawnUnder = spawnsUnder.isEmpty() || spawnsUnder.contains(world.getBlockId(i, j+1, k));
		boolean canStay = (spawns instanceof XieBlockLeaves) || spawns.canBlockStay(world, i, j, k);
				
		return canStay && canSpawnOn && canSpawnIn && canSpawnUnder
			&& (yMax<1 || j<=yMax) && (yMin<1 || j>=yMin);
	}
	
	public boolean canSpawnInThisBiome(World world, int i, int k) {
		return biomes==null || biomeCheck(world, i, k);
	}
	
	public void addMetaSpawnFactor(int meta, int spawnFactor) {
		if (metaFactors==null) metaFactors = new int[meta+1];
		else if (metaFactors.length<meta) {
			int newMeta[] = new int[meta+1];
			for (int i=0; i<metaFactors.length; i++) newMeta[i]=metaFactors[i];
			metaFactors=newMeta;
		}
		
		metaFactors[meta] = spawnFactor;
	}
	
	public void setClusterMetaFactors(Map<Byte, Integer> map) {
		int total = 0;
		for (Byte meta : map.keySet()) total+=map.get(meta);
		if (total<=0) {
			XieLog.log().logFailure("Cluster meta chance failure - negative or zero sum");
			return;
		}
		
		clusterMetas = new byte[total];
		int index = 0;
		for (Byte meta : map.keySet()) {
			for (int i=map.get(meta); i>0; i--) {
				if (index>=clusterMetas.length) {
					XieLog.log().logIncomplete("Array index exceeded for cluster metadata");
					break;
				} else clusterMetas[index++]=meta;
			}
		}
	}
	
	protected int getClusterMeta(Random random) {
		int i = random.nextInt(clusterMetas.length);
		return clusterMetas[i];
	}
	
	protected boolean biomeCheck(World world, int i, int k) {
		if (biomes==null) return true;
		
		String biome = world.getWorldChunkManager().getBiomeGenAt(i, k).biomeName.toLowerCase();
		for (String b : biomes)
			if (biome.contains(b)) return true;
		return false;
	}
	
	protected boolean attemptSpawn(World world, int i, int j, int k, int meta) {
		return attemptSpawn(world,i,j,k,meta,8,2,8);
	}
	
	protected boolean attemptSpawn(World world, int i, int j, int k, int meta, int di, int dj, int dk) {
		Random random = world.rand;
		
		if (clusterSizeMax<=1) {
			for(int l = 0; l < 64; l++) {
	            int xyz[] = getXYZ(random,i,j,k,di,dj,dk);
	            if(canSpawnHere(world, xyz[0], xyz[1], xyz[2])) {
	            	spawn(world, xyz[0], xyz[1], xyz[2], meta);
	            	return true;
	            }
	        }
		} else if (clump) {
			// find starting point
			int XYZ[] = null;
			for(int l = 0; l < 64; l++) {
	            int xyz [] = getXYZ(random,i,j,k,di,dj,dk);
	            if(canSpawnHere(world, xyz[0], xyz[1], xyz[2])) {
	            	if (clusterMetas!=null) meta = getClusterMeta(random);
	            	spawn(world, xyz[0], xyz[1], xyz[2], meta);
	            	XYZ = xyz;
	            }
	        }
			if (XYZ==null) return false;
			
			// build the clump
			int cluster = clusterSizeMax-1;
			int D = (int) Math.ceil((Math.sqrt(clusterSizeMax)-1)/2);
			for (int d=1; d<D; d++) {
				LinkedList<int[]> tuples = new LinkedList();
				for (int dz=-d; dz<d; dz++) tuples.add(new int[]{-d,dz});
				for (int dz=d; dz>-d; dz--) tuples.add(new int[]{d,dz});
				for (int dx=-d; dx<d; dx++) tuples.add(new int[]{dx,d});
				for (int dx=d; dx>-d; dx--) tuples.add(new int[]{dx,-d});
				Collections.shuffle(tuples,random);
				int N = tuples.size();
				for (int n=0; n<N; n++) {
					int[] dXdZ = tuples.pop();
					int[] xyz = new int[]{XYZ[0]+dXdZ[0],XYZ[1],XYZ[2]+dXdZ[1]};
					if(canSpawnHere(world, xyz[0], xyz[1], xyz[2])) {
						if (clusterMetas!=null) meta = getClusterMeta(random);
						spawn(world, xyz[0], xyz[1], xyz[2], meta);
						cluster--;
					}
					if (cluster==0) return true;
				}
			}
		} else {
			int cluster = clusterSizeMax;
			for(int l = 0; l < 64 && cluster > 0; l++) {
	            int xyz[] = getXYZ(random,i,j,k,8,2,8);
	            if(canSpawnHere(world, xyz[0], xyz[1], xyz[2])) {
	            	if (clusterMetas!=null) meta = getClusterMeta(random);
	            	spawn(world, xyz[0], xyz[1], xyz[2], meta);
	            	cluster--;
	            }
	        }
			return cluster==0;
		}
		
        return false;
	}
	
	// largely copy-pasta from WorldGenMinable
	protected boolean attemptSpawnOre(World world, int i, int j, int k, int meta, int dxz, int dy) {
		if (spawns == null) return false;
		boolean spawned = false;
		Random random = world.rand;
		float radians = random.nextFloat() * 3.141593F;
        double x1 = (float)(i + dxz) + (MathHelper.sin(radians) * (float)clusterSizeMax) / dxz;
        double x2 = (float)(i + dxz) - (MathHelper.sin(radians) * (float)clusterSizeMax) / dxz;
        double z1 = (float)(k + dxz) + (MathHelper.cos(radians) * (float)clusterSizeMax) / dxz;
        double z2 = (float)(k + dxz) - (MathHelper.cos(radians) * (float)clusterSizeMax) / dxz;
        double y1 = (j + random.nextInt(dy+1)) - dy;
        double y2 = (j + random.nextInt(dy+1)) - dy;
        for (int n = 0; n <= clusterSizeMax; n++) {
            double x = x1 + ((x2 - x1) * n) / clusterSizeMax;
            double y = y1 + ((y2 - y1) * n) / clusterSizeMax;
            double z = z1 + ((z2 - z1) * n) / clusterSizeMax;
            double var = (random.nextDouble() * clusterSizeMax) / 16D;
            double varxz = (double)(MathHelper.sin((n * 3.141593F) / clusterSizeMax) + 1.0F) * var + 1.0D;
            double vary = (double)(MathHelper.sin((n * 3.141593F) / clusterSizeMax) + 1.0F) * var + 1.0D;
            int xMin = MathHelper.floor_double(x - varxz / 2D);
            int yMin = MathHelper.floor_double(y - vary / 2D);
            int zMin = MathHelper.floor_double(z - varxz / 2D);
            int xMax = MathHelper.floor_double(x + varxz / 2D);
            int yMax = MathHelper.floor_double(y + vary / 2D);
            int zMax = MathHelper.floor_double(z + varxz / 2D);
            int count = 0;
            for (int xx = xMin; xx <= xMax; xx++){
                double xTest = ((xx + 0.5D) - x) / (varxz / 2D);
                if (xTest * xTest >= 1.0D) continue;
                for (int yy = yMin; yy <= yMax; yy++) {
                    double yTest = ((yy + 0.5D) - y) / (vary / 2D);
                    if (xTest * xTest + yTest * yTest >= 1.0D) continue;
                    for (int zz = zMin; zz <= zMax; zz++) {
                        double zTest = ((zz + 0.5D) - z) / (varxz / 2D);
                        if (xTest * xTest + yTest * yTest + zTest * zTest < 1.0D) {
                            if (canSpawnHere(world,xx,yy,zz)) {
                            	if (clusterMetas!=null) meta = getClusterMeta(random);
                            	spawn(world, xx, yy, zz, meta);
                            	if (!spawned) 
                            		System.out.println("Spawning "+spawns.getUnlocalizedName()+" meta ["+meta+"] @ ("+xx+","+yy+","+zz+")");
                            	spawned=true;
                            }
                        }
                    }
                }
            }
        }

        return spawned;
	}
	
	protected int[] getXYZ(Random random, int i, int j, int k, int dx, int dy, int dz) {
		int x = i; int y = j; int z = k;
		if (dx>0)
			x = (i + random.nextInt(2*dx)) - dx;
        if (dy>0)
        	y = (j + random.nextInt(2*dy)) - dy;
        if (dz>0)
        	z = (k + random.nextInt(2*dx)) - dx;
        return new int[]{x,y,z};
	}
	
	protected void spawn(World world, int x, int y, int z, int meta) {
		if (spawns == null) return;
		if (spawns instanceof XieBlockLeaves) {
    		XieBlockLeaves leafBlock = (XieBlockLeaves) spawns;
    		leafBlock.generate(world, x, y, z, meta, true);
    	} else {
    		world.setBlockAndMetadata(x, y, z, spawns.blockID, meta);
    	}
	}
	
	@Override
	public String toString() {
		if (spawns == null) return "null";
		String rtn="Spawnable: "+spawns.getUnlocalizedName()+" factor: "+spawnFactor+"\nSpawns On: ";
		for (int i : spawnsOn) rtn+=i+" ";
		rtn+="\nSpawns In: ";
		for (int i : spawnsIn) rtn+=i+" ";
		
		return rtn+"\n";
	}
	
	public static void generate(List<Spawnable> spawnList, World world, Random random, int i, int k) {
		if (spawnList==null) return;
		
		Collections.shuffle(spawnList,random);
		
		for (Spawnable spawn : spawnList) {
			if (spawn.spawnFactor<1 || !spawn.canSpawnInThisBiome(world,i,k)) continue;
			
			if (spawn.metaFactors==null) {
				if (random.nextInt(spawn.spawnFactor)==0) {
					spawn.attemptSpawn(world,i,world.getHeightValue(i,k),k,spawn.meta);
				}
			} else {
				int meta = random.nextInt(spawn.metaFactors.length);
				if (random.nextInt(spawn.metaFactors[meta])==0) {
					spawn.attemptSpawn(world,i,world.getHeightValue(i,k),k,spawn.meta);
				}
			}
		}
	}
	
	public static void generateUnderground(List<Spawnable> spawnList, World world, Random random, int i, int k) {
		if (spawnList==null) return;
		
		Collections.shuffle(spawnList,random);
		
		for (Spawnable spawn : spawnList) {
			if (spawn.spawnFactor<1) continue;
			
			// find dungeon
			int j = -1;
			int yMax = world.getHeightValue(i,k);
			if (spawn.yMax>0) yMax=spawn.yMax;
			for (int y = 1; y<=yMax; y++) {
				if (world.getBlockId(i, y, k)==0 && !world.canBlockSeeTheSky(i, y, k)) {
					j=y;
					break;
				}
			}
			if (j==-1) continue;
			
			if (spawn.metaFactors==null) {
				if (random.nextInt(spawn.spawnFactor)==0) {
					spawn.attemptSpawn(world,i,j,k,spawn.meta,1,0,1);
				}
			} else {
				int meta = random.nextInt(spawn.metaFactors.length);
				if (random.nextInt(spawn.metaFactors[meta])==0) {
					spawn.attemptSpawn(world,i,j,k,spawn.meta,1,0,1);
				}
			}
		}
	}
	
	public static void generateOre(List<Spawnable> spawnList, World world, Random random, int i, int k) {
		if (spawnList==null) return;
		
		Collections.shuffle(spawnList,random);
		
		for (Spawnable spawn : spawnList) {
			if (spawn.spawnFactor<1) continue;
			
			// determine height
			int yMin = spawn.yMin>0?spawn.yMin:1;
			int yMax = spawn.yMax>0?spawn.yMax:world.getHeightValue(i,k);
			int j;
			if (yMax>yMin) j = random.nextInt(yMax-yMin) + yMin;
			else j = random.nextInt(world.getHeight());
			
			if (spawn.metaFactors==null) {
				if (random.nextInt(spawn.spawnFactor)==0) {
					if (spawn.attemptSpawnOre(world,i,j,k,spawn.meta,8,2)) {
						break;
					}
				}
			} else {
				int meta = random.nextInt(spawn.metaFactors.length);
				if (random.nextInt(spawn.metaFactors[meta])==0) {
					if (spawn.attemptSpawnOre(world,i,j,k,spawn.meta,8,2)) {
						break;
					}
				}
			}
		}
	}
	
	public static void generateSurface(World world, Random random, int i, int k) {
		generate(surface, world, random, i, k);
		generateUnderground(dungeon, world, random, i, k);
		generateOre(ore, world, random, i, k);
	}
	
	public static void generateNether(World world, Random random, int i, int k) {
		generateOre(nether, world, random, i, k);
	}
	
}
