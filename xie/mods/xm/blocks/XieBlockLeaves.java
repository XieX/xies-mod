package xie.mods.xm.blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.StepSound;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import xie.mods.xm.util.Drop;
import xie.mods.xm.util.Droppables;
import xie.mods.xm.util.Xie;
import xie.mods.xm.util.XieTex;
import xie.mods.xm.world.TreeGenerator;


public class XieBlockLeaves extends BlockLeaves implements IXieBlock {
	public boolean enableColorisation = false;
    protected int adjacentTreeBlocks[];
    private Droppables sheds;
    public int shedSaturation = 0;
    public int trunkID;
    protected int trunkMeta[];
    protected Shape shape;
    public enum Shape { OAK, TALL, CONIFER, BIRCH };
    
	public XieBlockLeaves(String name, int leafID) {
		super(leafID, Block.leaves.blockIndexInTexture);
		
		setLightOpacity(1);
		disableStats();
		//setRequiresSelfNotify();
		Xie.setFlammability(leafID, 30, 60);
		setGraphicsLevel(true);
		renderType=0;
		trunkID = Block.wood.blockID;
		setBlockName(name);
		setStepSound(Block.soundGrassFootstep);
		setHardness(0.2F);
    }
	
	public void setShedSaturation(int s) {shedSaturation=s;}
	
	public void setShape(Shape s) { shape=s; }
	public void setShape(String s) {
		s = s.trim().toLowerCase();
		if (s.equals("birch")) {
			setShape(Shape.BIRCH);
		} else if (s.equals("conifer") || s.equals("pine")) {
			setShape(Shape.CONIFER);
		} else if (s.equals("tall") || s.equals("tallpine") || s.equals("tallconifer")) {
			setShape(Shape.TALL);
		} else {
			setShape(Shape.OAK);
		}
	}

	public void setSheds(Droppables sheds) { this.sheds=sheds; }
	
	public void setTrunkMeta(int meta[]) {	trunkMeta=meta; }
	    
    public static final short keyStoneBit = 4;
    protected boolean isKeyStone(int metadata) {
    	return (metadata & keyStoneBit) == keyStoneBit;
    }
   
    protected final static int[][] ADJ = {{1,0},{0,1},{-1,0},{0,-1},{1,1},{-1,-1},{1,-1},{-1,1}};
    protected void shed(World world, int i, int j, int k, int meta) {
    	int dir = world.rand.nextInt(ADJ.length);
    	int dx=ADJ[dir][0];
    	int dy=0;
    	int dz=ADJ[dir][1];
    	int blockID;
    	do {
    		dy--;
    		blockID = world.getBlockId(i+dx,j+dy,k+dz);
    		if (dy<-100) break; // just in case
    	} while (blockID!=0);

    	dy++;
    	
    	if (shedSaturation>0) ifSheds().dropBlockAsItem(world, i+dx, j+dy, k+dz, meta, 0, shedSaturation);
    	else ifSheds().dropBlockAsItem(world, i+dx, j+dy, k+dz, meta, 0);
    }
   
    protected void keyStoneMigration(World world, int i, int j, int k, int meta) {
    	int count=0;
    	int dy=0;
    	for (int n=world.rand.nextInt(ADJ.length); count<3*ADJ.length; n=(n==ADJ.length-1)?0:n+1) {
    		int dx = ADJ[n][0];
    		int dz = ADJ[n][1];
    		int targetID = world.getBlockId(i+dx,j+dy,k+dz);
    		int targetMeta = metaFilter(world.getBlockMetadata(i+dx,j+dy,k+dz));
    		if (targetID==blockID && targetMeta==meta) {
    			world.setBlockAndMetadata(i+dx, j+dy, k+dz, targetID, targetMeta | keyStoneBit);
    			return;
    		}
    		if (++count==ADJ.length) dy=-1;
    		if (count==2*ADJ.length) dy=1;
    	}
    }
    
    @Override
    public int getBlockColor() {
    	if (enableColorisation) return super.getBlockColor();
    	else return 0xffffff;
    }
    
    @Override
    public int getRenderColor(int i) {
    	if (enableColorisation) return super.getRenderColor(i);
    	else return 0xffffff;
    }

    @Override
    public int colorMultiplier(IBlockAccess block, int i, int j, int k) {
    	if (enableColorisation) return super.colorMultiplier(block, i, j, k);
    	else return 0xffffff;
    }

    // largely copy-pasta from super class
    @Override
    public void updateTick(World world, int i, int j, int k, Random random) {
        if(world.isRemote) return;
        
        int meta = world.getBlockMetadata(i, j, k);
        // the keystone block is the one just above the topmost log, and triggers leaf decay
        if (isKeyStone(meta)) {
	        // check for drop spawn
	        if (ifSheds()!=null && !ifSheds().isEmpty()) shed(world, i, j, k, metaFilter(meta));
	        
	        if (!Xie.isLogBlock(world,i,j-1,k)) {
	        	Xie.setDecayFlag(world, i, j, k);
	        	meta |= 8;
	        }
        }
        
        // check for decay
        if(Xie.isDecayFlagSet(meta)) {
            byte d = 4;
            byte size = 32;
            int j1 = size * size;
            int k1 = size / 2;
            if(adjacentTreeBlocks == null) adjacentTreeBlocks = new int[size * size * size];

            if(world.checkChunksExist(i-d-1, j-d-1, k-d-1, i+d+1, j+d+1, k+d+1)) {
                for(int l1 = -d; l1 <= d; l1++) {
                    for(int k2 = -d; k2 <= d; k2++) {
                        for(int i3 = -d; i3 <= d; i3++) {
                            if (Xie.isLogBlock(world, i + l1, j + k2, k + i3)) {
                                adjacentTreeBlocks[(l1 + k1) * j1 + (k2 + k1) * size + (i3 + k1)] = 0;
                                continue;
                            }
                            if (Xie.isLeafBlock(world, i + l1, j + k2, k + i3)) {
                                adjacentTreeBlocks[(l1 + k1) * j1 + (k2 + k1) * size + (i3 + k1)] = -2;
                            } else {
                                adjacentTreeBlocks[(l1 + k1) * j1 + (k2 + k1) * size + (i3 + k1)] = -1;
                            }
                        }
                    }
                }

                for(int i2 = 1; i2 <= 4; i2++) {
                    for(int l2 = -d; l2 <= d; l2++) {
                        for(int j3 = -d; j3 <= d; j3++) {
                            for(int l3 = -d; l3 <= d; l3++) {
                                if(adjacentTreeBlocks[(l2 + k1) * j1 + (j3 + k1) * size + (l3 + k1)] != i2 - 1) 
                                	continue;
                                if(adjacentTreeBlocks[((l2 + k1) - 1) * j1 + (j3 + k1) * size + (l3 + k1)] == -2)
                                {
                                    adjacentTreeBlocks[((l2 + k1) - 1) * j1 + (j3 + k1) * size + (l3 + k1)] = i2;
                                }
                                if(adjacentTreeBlocks[(l2 + k1 + 1) * j1 + (j3 + k1) * size + (l3 + k1)] == -2)
                                {
                                    adjacentTreeBlocks[(l2 + k1 + 1) * j1 + (j3 + k1) * size + (l3 + k1)] = i2;
                                }
                                if(adjacentTreeBlocks[(l2 + k1) * j1 + ((j3 + k1) - 1) * size + (l3 + k1)] == -2)
                                {
                                    adjacentTreeBlocks[(l2 + k1) * j1 + ((j3 + k1) - 1) * size + (l3 + k1)] = i2;
                                }
                                if(adjacentTreeBlocks[(l2 + k1) * j1 + (j3 + k1 + 1) * size + (l3 + k1)] == -2)
                                {
                                    adjacentTreeBlocks[(l2 + k1) * j1 + (j3 + k1 + 1) * size + (l3 + k1)] = i2;
                                }
                                if(adjacentTreeBlocks[(l2 + k1) * j1 + (j3 + k1) * size + ((l3 + k1) - 1)] == -2)
                                {
                                    adjacentTreeBlocks[(l2 + k1) * j1 + (j3 + k1) * size + ((l3 + k1) - 1)] = i2;
                                }
                                if(adjacentTreeBlocks[(l2 + k1) * j1 + (j3 + k1) * size + (l3 + k1 + 1)] == -2)
                                {
                                    adjacentTreeBlocks[(l2 + k1) * j1 + (j3 + k1) * size + (l3 + k1 + 1)] = i2;
                                }
                            }

                        }

                    }

                }

            }
            int j2 = adjacentTreeBlocks[k1 * j1 + k1 * size + k1];
            if(j2 >= 0) {
            	world.setBlockMetadata(i, j, k, meta & -9);
            } else {
            	removeLeaves(world, i, j, k);
            }
        }
        
        //super.updateTick(world, i, j, k, random);
    }
    
    @Override
	public void dropBlockAsItemWithChance(World world, int i, int j, int k, int l, float f, int multiplier) {
        if (world.isRemote) return;
    	
    	if (drops==null) {
        	// no drops defined, drop self if required
        	if (dropBlock) {
        		if (inheritMeta) Xie.spawnItem(new ItemStack(this,1,l), world, i, j, k); 
        		else Xie.spawnItem(new ItemStack(this), world, i, j, k);
        	}
        	return;
        } else drops.dropBlockAsItemWithChance(world, i, j, k, metaFilter(l), f, multiplier);
    }
    
    public int getPlantGrowth(World world, int i, int j, int k) {
    	return metaFilter(world.getBlockMetadata(i, j, k));
    }
    
    public void setTrunk(int id) { trunkID=id; }
    public void setTrunk(Block b) {
    	if (b!=null) trunkID=b.blockID; 
    }
 
    public int metaFilter(int meta) {
    	return meta & 3;
    }
    
    public boolean generate(World world, int i, int j, int k, int meta, boolean natural) {
    	if (shape==null) shape=Shape.OAK;
    	if (world.isRemote) return true;
    	else {
    		switch (shape) {
    		case BIRCH: return TreeGenerator.generateOak(world, this, i, j, k, meta, natural);
    		case CONIFER: return TreeGenerator.generateConifer(world, this, i, j, k, meta, natural);
    		case TALL: return TreeGenerator.generateTallConifer(world, this, i, j, k, meta, natural);
    		default: return TreeGenerator.generateOak(world, this, i, j, k, meta, natural);
    		}
    	}
    }
    
    public int getTrunkMeta(int meta) {
		if (trunkMeta==null) return 0;
		if (trunkMeta.length>meta) return trunkMeta[meta];
		else return 0;
	}
    
	protected void removeLeaves(World world, int i, int j, int k) {
        dropBlockAsItem(world, i, j, k, world.getBlockMetadata(i, j, k), 0);
        world.setBlockWithNotify(i, j, k, 0);
    }

	@Override
    public void setGraphicsLevel(boolean flag) {
        graphicsLevel = flag;
        //blockIndexInTexture = baseIndexInPNG + (flag ? 0 : 1); // TODO handle fast graphics setting
    }

    @Override
	public boolean isOpaqueCube() {
    	return false;
    }

    @Override
    public boolean isShearable(ItemStack item, World world, int x, int y, int z) {
        return true;
    }

    @Override
    public ArrayList<ItemStack> onSheared(ItemStack item, World world, int x, int y, int z, int fortune) {
        ArrayList<ItemStack> arr = new ArrayList<ItemStack>();
        int meta = metaFilter(world.getBlockMetadata(x,y,z));
        arr.add(new ItemStack(this,1,meta));
        return arr;
    }
    
    @Override
    public void beginLeavesDecay(World world, int x, int y, int z) {
        Xie.setDecayFlag(world, x, y, z);
    }
    
    @Override
    public boolean isLeaves(World world, int x, int y, int z) {
    	return true;
    }
    
    // Copy-Pasta from XieBlock
    protected Droppables drops = new Droppables();
	
	public boolean damageDrops;
	public boolean dropBlock = true;
	public boolean inheritMeta = true;
	
	protected XieTex tex;
	protected String[] names;
	
	public int renderType;
	public boolean renderNormally = true;
	protected Boolean isOpaque;
	
	protected int becomesWhenDestroyed[];
	
	protected float blockBounds[];
	protected float blockBoundsByMeta[][];
	
	@Override
	public Block getBlock() {
		return this;
	}

	@Override
	public void setBlockBounds(float boundsByMeta[][]) { blockBoundsByMeta=boundsByMeta; }

	public float[] getBlockBounds() { return blockBounds; }
	public boolean boundsVaryByMeta() { return blockBoundsByMeta!=null; }

	protected void updateBoundsForMeta(int meta) {
		if (blockBoundsByMeta==null) return;
		if (blockBoundsByMeta.length>meta) setBlockBounds(blockBoundsByMeta[meta]);
		else setBlockBounds(blockBoundsByMeta[blockBoundsByMeta.length-1]);
	}
	
	protected List<Block> canBePlacedOn;
	@Override
	public void setCanBePlacedOn(List<Block> blocks) {
		canBePlacedOn = blocks;
	}	
	
	@Override
	public boolean canPlaceBlockAt(World world, int i, int j, int k) {
		if (canBePlacedOn==null) return super.canPlaceBlockAt(world, i, j, k);
		if (canBePlacedOn.isEmpty()) return super.canPlaceBlockAt(world, i, j, k);
		Block b = Block.blocksList[world.getBlockId(i, j, k)];
		return canBePlacedOn.contains(b);
    }
	
	@Override
	public void addDrop(Drop drop) {
		drops.add(drop);
	}
	
	@Override
	public void setDrops(Droppables drops) {
		this.drops=drops;
	}
	
	@Override
	public int getRenderType()
    {
        return renderType;
    }
    
    @Override
	public boolean renderAsNormalBlock()
    {
    	return renderNormally;
    }
    
    @Override
	public void setRenderType(int r) {
    	renderType=r;
    	renderNormally=false;
    	if (Xie.isTransparentRenderType(r)) setIsOpaque(false);
    }
    
    @Override
	public XieTex getTex() {
    	return tex;
    }
    
    @Override
	public void setTex(XieTex t) {
    	isDefaultTexture=t.getTexFile()==null;
    	tex=t;
    }
    
    @Override
	public int getBlockTextureFromSide(int i) {
    	return getBlockTextureFromSideAndMetadata(i,0);
    }
 
	// texture IDs, tex[side][meta]
    @Override
	public int getBlockTextureFromSideAndMetadata(int i, int j) {
    	return tex.getTexFromSideAndMeta(i, metaFilter(j));
    }
    
    // not really used
    @Override
	public int quantityDropped(Random random) {
        return 0;
    }

    // not really used
    @Override
	public int idDropped(int i, Random random, int meta) {
    	return -1;
    }
    
    @Override
	public void onBlockDestroyedByPlayer(World world, int i, int j, int k, int l) {
    	if (becomesWhenDestroyed!=null) {
    		if (becomesWhenDestroyed.length>1)
    			world.setBlockAndMetadataWithNotify(i, j, k, becomesWhenDestroyed[0], becomesWhenDestroyed[1]);
    		else world.setBlockAndMetadataWithNotify(i, j, k, becomesWhenDestroyed[0], l);
    	}
    }
    
    @Override
	public void setBlockBoundsBasedOnState(IBlockAccess iblockaccess, int i, int j, int k) {
    	if (blockBoundsByMeta!=null) 
    		updateBoundsForMeta(iblockaccess.getBlockMetadata(i, j, k));
	}

    @Override
	public int damageDropped(int i) {
        if (damageDrops) return i;
    	return 0;
    }
	
    @Override
	public Block setStepSound(StepSound stepsound) {
        return super.setStepSound(stepsound);
    }
    
    @Override
	public Block setHardness(float f) {
    	return super.setHardness(f);
    }

    @Override
	public void setBlockBounds(float[] bounds) {
		blockBounds = bounds;
		if (bounds==null || bounds.length<6) return;
		else setBlockBounds(bounds[0],bounds[1],bounds[2],bounds[3],bounds[4],bounds[5]);
		
		for (int i=0; i<6; i++) {
			if (i<3?bounds[i]!=0:bounds[i]!=1) {
				setIsOpaque(false);
				break;
			}
		}
	}    
	
	@Override
	public void setIsOpaque(boolean b) { 
    	isOpaque=b;
    	renderNormally=false;
    	Block.opaqueCubeLookup[blockID]=b;
    	if (!b) Block.lightOpacity[blockID]=0;
    	else Block.lightOpacity[blockID]=255;
    }

	@Override
	public boolean hasSubTypes() {
		return names!=null;
	}

	@Override
	public String getBlockNameIS(ItemStack itemstack) {
		int i = itemstack.getItemDamage();
		if (names==null || names.length <= i) return getBlockName();
		else return names[i];
	}

	@Override
	public void setNames(String[] string) {
		names = string;
	}

	@Override
	public String[] getNames() { return names; }
	
	@Override
	public String getTextureFile() {
		return Xie.textureWrapper(this);
	}

	@Override
	public void setBecomesWhenDestroyed(int[] bwd) {
		becomesWhenDestroyed=bwd;
	} 
	
	protected boolean walkThroughable = false;
	@Override
	public void walkThroughable(boolean b) {walkThroughable=b;}
	@Override
	public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int i, int j, int k) {
	   	if (walkThroughable) return null;
    	else return super.getCollisionBoundingBoxFromPool(world, i, j, k);
    }
    
	@Override
	public Block setLightValue(float f) { return super.setLightValue(f); }
    
	@Override
	public Block setLightOpacity(int i) { return super.setLightOpacity(i); }

	protected boolean isSpikey = false;
	
	@Override
	public void setIsSpikey(boolean b) { isSpikey = b; }
	
	@Override
	public void onEntityCollidedWithBlock(World world, int i, int j, int k, Entity entity) {
	    if (isSpikey && !world.isRemote) entity.attackEntityFrom(DamageSource.generic, 1);
	    super.onEntityCollidedWithBlock(world, i, j, k, entity);
	}

	@Override
	public Object getGuiElement(EntityPlayer player, World world, int i, int j, int k) {
		// TODO Auto-generated method stub
		return null;
	}

	public Droppables ifSheds() {
		return sheds;
	}
	
    @SideOnly(Side.CLIENT)
    @Override
    public void getSubBlocks(int id, CreativeTabs tab, List list) {
    	list.add(new ItemStack(id,1,0));
    	if (names==null) return;
    	
    	int i=0;
    	while (++i<names.length) list.add(new ItemStack(id, 1, i));
    }
}
