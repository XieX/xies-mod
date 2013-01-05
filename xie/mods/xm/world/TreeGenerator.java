package xie.mods.xm.world;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import xie.mods.xm.blocks.XieBlockLeaves;
import xie.mods.xm.util.Xie;

public class TreeGenerator {
   
	public static enum Shape { OAK, TALL, CONIFER, BIRCH };
	
	public static Shape getShape(String s) {
		s = s.trim().toLowerCase();
		if (s.equals("birch")) {
			return Shape.BIRCH;
		} else if (s.equals("conifer") || s.equals("pine")) {
			return Shape.CONIFER;
		} else if (s.equals("tall") || s.equals("tallpine") || s.equals("tallconifer")) {
			return Shape.TALL;
		} else {
			return Shape.OAK;
		}
	}
    
    public static boolean generateOak(World world, XieBlockLeaves leaves, int i, int j, int k, int meta, boolean natural) {
    	Random random = world.rand;
 
    	int l = random.nextInt(3) + 4;
        boolean flag = true;
        if(j < 1 || j + 2 > 128) return false;
        for(int i1 = j; i1 <= j + 2; i1++) {
            byte byte0 = 1;
            if(i1 == j) {
                byte0 = 0;
            }
            if(i1 >= (j + 1 + l) - 2) {
                byte0 = 2;
            }
            for(int i2 = i - byte0; i2 <= i + byte0 && flag; i2++) {
                for(int l2 = k - byte0; l2 <= k + byte0 && flag; l2++) {
                    if(i1 >= 0 && i1 < 128) {
                        int j3 = world.getBlockId(i2, i1, l2);
                        if(j3 != 0 && j3 != leaves.blockID) {
                            flag = false;
                        }
                    } else flag = false;
                }
            }
        }

        if(!flag) return false;
        
        int j1 = world.getBlockId(i, j - 1, k);
        if (j1==Block.grass.leaves.blockID) world.setBlock(i, j - 1, k, Block.dirt.leaves.blockID);
        for(int k1 = (j - 3) + l; k1 <= j + l; k1++) {
            int j2 = k1 - (j + l);
            int i3 = 1 - j2 / 2;
            for(int k3 = i - i3; k3 <= i + i3; k3++) {
                int l3 = k3 - i;
                for(int i4 = k - i3; i4 <= k + i3; i4++) {
                    int j4 = i4 - k;
                    if ((Math.abs(l3) != i3 || Math.abs(j4) != i3 || random.nextInt(2) != 0 && j2 != 0) && !Block.opaqueCubeLookup[world.getBlockId(k3, k1, i4)])  {
                    	Xie.setBlockAndMeta(world, k3, k1, i4, leaves.blockID, meta);
                    }
                }
            }
        }

        for (int y = 0; y < l; y++) {
            int k2 = world.getBlockId(i, j + y, k);
            if(k2 == 0 || k2 == leaves.blockID) {
            	Xie.setBlockAndMeta(world, i, j + y, k, leaves.trunkID, leaves.getTrunkMeta(meta));
            }
        }
        // set keystone block
        if (leaves.ifSheds()!=null && !natural)
        	Xie.setBlockAndMeta(world, i, j+l, k, leaves.blockID, meta | XieBlockLeaves.keyStoneBit);

        return true;
    }
    
    public static boolean generateConifer(World world, XieBlockLeaves leaves, int i, int j, int k, int meta, boolean natural) {
    	Random random = world.rand;
    	int l = random.nextInt(4) + 6;
        int i1 = 1 + random.nextInt(2);
        int j1 = l - i1;
        int k1 = 2 + random.nextInt(2);
        boolean flag = true;
        if(j < 1 || j + l + 1 > world.getHeight()) {
            return false;
        }
        for(int l1 = j; l1 <= j + 1 + l && flag; l1++) {
            int j2 = 1;
            if(l1 - j < i1) {
                j2 = 0;
            } else {
                j2 = k1;
            }
            for(int l2 = i - j2; l2 <= i + j2 && flag; l2++) {
                for(int j3 = k - j2; j3 <= k + j2 && flag; j3++) {
                    if(l1 >= 0 && l1 < world.getHeight()) {
                        int k3 = world.getBlockId(l2, l1, j3);
                        if(k3 != 0 && k3 != leaves.blockID)  {
                            flag = false;
                        }
                    } else  {
                        flag = false;
                    }
                }

            }

        }

        if(!flag)  {
            return false;
        }
        int i2 = world.getBlockId(i, j - 1, k);
        if(i2 != Block.grass.leaves.blockID && i2 != Block.dirt.leaves.blockID || j >= world.getHeight() - l - 1) {
            return false;
        }
        world.setBlock(i, j - 1, k, Block.dirt.leaves.blockID);
        int k2 = random.nextInt(2);
        int i3 = 1;
        boolean flag1 = false;
        for(int l3 = 0; l3 <= j1; l3++) {
            int j4 = (j + l) - l3;
            for(int l4 = i - k2; l4 <= i + k2; l4++) {
                int j5 = l4 - i;
                for(int k5 = k - k2; k5 <= k + k2; k5++) {
                    int l5 = k5 - k;
                    if((Math.abs(j5) != k2 || Math.abs(l5) != k2 || k2 <= 0) && !Block.opaqueCubeLookup[world.getBlockId(l4, j4, k5)]) {
                    	Xie.setBlockAndMeta(world, l4, j4, k5, leaves.blockID, meta);
                    }
                }
            }

            if(k2 >= i3) {
                k2 = ((flag1) ? 1 : 0);
                flag1 = true;
                if(++i3 > k1) {
                    i3 = k1;
                }
            } else {
                k2++;
            }
        }

        int i4 = random.nextInt(3);
        for(int k4 = 0; k4 < l - i4; k4++) {
            int i5 = world.getBlockId(i, j + k4, k);
            if(i5 == 0 || i5 == leaves.blockID) {
            	Xie.setBlockAndMeta(world, i, j + k4, k, leaves.trunkID, leaves.getTrunkMeta(meta));
            }
        }
        // set keystone block - the one that leaves.sheds drops
        if (leaves.ifSheds()!=null && !natural)
        	Xie.setBlockAndMeta(world, i, j+l, k, leaves.blockID, meta | XieBlockLeaves.keyStoneBit);

        return true;
    }
    
    public static boolean generateTallConifer(World world, XieBlockLeaves leaves, int i, int j, int k, int meta, boolean natural) {
        Random random = world.rand;
    	int l = random.nextInt(5) + 7;
        int i1 = l - random.nextInt(2) - 3;
        int j1 = l - i1;
        int k1 = 1 + random.nextInt(j1 + 1);
        boolean flag = true;
        if(j < 1 || j + l + 1 > world.getHeight()) {
            return false;
        }
        for(int l1 = j; l1 <= j + 1 + l && flag; l1++) {
            int j2 = 1;
            if(l1 - j < i1) {
                j2 = 0;
            } else {
                j2 = k1;
            }
            for(int l2 = i - j2; l2 <= i + j2 && flag; l2++) {
                for(int k3 = k - j2; k3 <= k + j2 && flag; k3++)  {
                    if(l1 >= 0 && l1 < world.getHeight()) {
                        int j4 = world.getBlockId(l2, l1, k3);
                        if(j4 != 0 && j4 != leaves.blockID) flag = false;
                    } else flag = false;
                }
            }
        }

        if(!flag) {
            return false;
        }
        int i2 = world.getBlockId(i, j - 1, k);
        if(i2 != Block.grass.leaves.blockID && i2 != Block.dirt.leaves.blockID || j >= world.getHeight() - l - 1) {
            return false;
        }
        world.setBlock(i, j - 1, k, Block.dirt.leaves.blockID);
        int k2 = 0;
        for(int i3 = j + l; i3 >= j + i1; i3--)
        {
            for(int l3 = i - k2; l3 <= i + k2; l3++) {
                int k4 = l3 - i;
                for(int l4 = k - k2; l4 <= k + k2; l4++) {
                    int i5 = l4 - k;
                    if((Math.abs(k4) != k2 || Math.abs(i5) != k2 || k2 <= 0) && !Block.opaqueCubeLookup[world.getBlockId(l3, i3, l4)]) {
                    	Xie.setBlockAndMeta(world, l3, i3, l4, leaves.blockID, meta);
                    }
                }

            }

            if(k2 >= 1 && i3 == j + i1 + 1) {
                k2--;
                continue;
            }
            if(k2 < k1) {
                k2++;
            }
        }

        for(int j3 = 0; j3 < l - 1; j3++) {
            int i4 = world.getBlockId(i, j + j3, k);
            if(i4 == 0 || i4 == leaves.blockID) {
            	Xie.setBlockAndMeta(world, i, j + j3, k, leaves.trunkID, leaves.getTrunkMeta(meta));
            }
        }
        // set keystone block
        if (leaves.ifSheds()!=null && !natural)
        	Xie.setBlockAndMeta(world, i, j+l, k, leaves.blockID, meta | XieBlockLeaves.keyStoneBit);

        return true;
    }
}
