package mod.xie.xm.blocks;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import mod.xie.xm.util.Drop;
import mod.xie.xm.util.Droppables;
import mod.xie.xm.util.Xie;
import mod.xie.xm.util.XieTex;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.StepSound;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;



public class XieBlockPlant extends BlockCrops implements IXieBlock {

	// static list of all plants
	public static List<XieBlockPlant> plants = new LinkedList<XieBlockPlant>();
	public static List<XieBlockPlant> spawnList = new LinkedList<XieBlockPlant>();
	
	// growth particulars
	public int growthRate = 20;
	public int growthHeight = 1;
	public boolean reproductive = false;
	public int reproductionRate = 20;
	public int growsInto = -1;
	public int maxGrowth = 15;
	public int growsNearWater = 0;
	public int lightLevel = 8;
	public int growsOn[] = null;
	public int growsAgainst[] = null;
	// TODO perhaps add other 'distance to this blockID' requirements

	public boolean fertilisable = true;
	
	
	// seeds
	//public int seedDrop = -1;
	//public int seedDropMax = 3;
	
	// other settings
	public boolean isVineLike = false;
	
	// constructor
	public XieBlockPlant(String plantName, int plantID) {
		super(plantID, Block.plantRed.blockIndexInTexture);
		setBlockName(plantName);
		
		// block settings
		setTickRandomly(true);
		setHardness(0.0F);
        this.setStepSound(Block.soundGrassFootstep);
        
        // add this plant to the list
        XieBlockPlant.plants.add(this);
        setFlammability(60,100); // default as tall grass
        
        // default to full block size
        setBlockBounds(0F,0F,0F,1F,1F,1F);
        
        // default droppables constructor
        drops = new Droppables();
        
        
	}
	
	@Override
    public boolean canPlaceBlockAt(World world, int i, int j, int k) {
		if (canBePlacedOn!=null) 
			if (!canBePlacedOn.isEmpty()){
				Block b = Block.blocksList[world.getBlockId(i, j, k)];
				if (!canBePlacedOn.contains(b)) return false;
			}
		
		int bid = world.getBlockId(i, j, k);
        return canBePlantedOn(world.getBlockId(i, j - 1, k))
        		&& (!isVineLike || canThisPlantGrowAgainstThisBlockID(getBackingBlockID(world, i, j, k)));
    }
    
	// tests to see if the plant can remain where it is
    protected final void testThatBlockCanStillGrowHere(World world, int i, int j, int k) {
        if(!canBlockStay(world, i, j, k))
        {
            dropBlockAsItem(world, i, j, k, world.getBlockMetadata(i, j, k),0);
            world.setBlockWithNotify(i, j, k, 0); // TODO implement "growing in" other blocks, replace 0 here with growsIn blockID
        }
    }
    
    @Override
    public void onNeighborBlockChange(World world, int i, int j, int k, int l) {
    	if (!canBlockStay(world, i, j, k)) {
    		dropBlockAsItem(world, i, j, k, world.getBlockMetadata(i, j, k), 0);
            world.setBlockWithNotify(i, j, k, 0);
    	}
    }

    @Override
    public boolean canBlockStay(World world, int i, int j, int k) {
        return canGrowOn(world.getBlockId(i, j - 1, k))
        	&& (world.getFullBlockLightValue(i, j, k) >= lightLevel || world.canBlockSeeTheSky(i, j, k))
        	&& (growsNearWater<1 || isThisFarFromWater(world, i, j, k, growsNearWater))
        	&& (!isVineLike || canThisPlantGrowAgainstThisBlockID(getBackingBlockID(world, i, j, k)));
        	
    }
    
    protected int getBackingBlockID(World world, int i, int j, int k) {
    	int o = getOrientation(world.getBlockMetadata(i,j,k));
    	
    	// TODO verify these are correct
    	switch (o) {
    		case 0: return world.getBlockId(i,j,k+1);
    		case 1: return world.getBlockId(i+1,j,k);
    		case 2: return world.getBlockId(i,j,k-1);
    		case 3: return world.getBlockId(i-1,j,k);
    		default: return -1;
    	}
    }
    
    protected int getOrientation(int l) {
    	return (l & 12) / 4; // bitmask for bits 2 and 3, and effectively bitshift of 2
    }
    
    protected boolean isThisFarFromWater(World world, int i, int j, int k, int d) {
    	if (d<1) return true;
    	
    	for (int x=-d; x<=d; x++) for (int y=-d; y<=d; y++) for (int z=-d; z<=d; z++) {
    		int thisID = world.getBlockId(i+x, j+y, k+z);
    		if (thisID==Block.waterStill.blockID || thisID==Block.waterMoving.blockID) return true;
    	}
    	
    	return false;
    }

    public boolean canGrowOn(int id) {
    	if (growsOn==null) return true;
    	if (growthHeight>1 && id==blockID) return true;
    	for (int i=0; i<growsOn.length; i++) if (growsOn[i]==id) return true;
    	return false;
    }
    
    public boolean canBePlantedOn(int id) {
    	if (growsOn==null) return true;
    	for (int i : growsOn) if (id==i) return true;
    	return false;
    }
    
    public boolean canThisPlantGrowAgainstThisBlockID(int id) {
    	if (growsAgainst==null) return true;
    	for (int i=0; i<growsAgainst.length; i++) if (growsAgainst[i]==id) return true;
    	return false;
    }
        
    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess iblockaccess, int i, int j, int k) {	 
    	if (blockBoundsByMeta!=null) updateBoundsForMeta(iblockaccess.getBlockMetadata(i,j,k));
    	
        if(!isVineLike)
        {
        	super.setBlockBoundsBasedOnState(iblockaccess, i, j, k);
            return;
        }
        
        int o = getOrientation(iblockaccess.getBlockMetadata(i, j, k));
        float height = 1.0F;
        float width = 1.0F;
        float depth = 0.2F;
        
        // TODO check these
        switch (o) {
        	case 0: setBlockBounds(1.0F - depth, 1.0F, 0.F, height, 0.0F, width);
        	case 1: setBlockBounds(0.0F, width, 0.F, height, 1.0F - depth, 1.0F);
        	case 2: setBlockBounds(0.0F, depth, 0.F, height, 0.0F, width);
        	case 3: setBlockBounds(0.0F, width, 0.F, height, 0.0F, depth);
        	default: setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        }
    }
	
    @Override
    public void fertilize(World world, int i, int j, int k) {
		if (growsInto>0) growIntoSomethingElse(world, i, j, k);
		else {
			if (growthHeight>1) {
				if (getHeight(world, i, j, k)==1) {
					for (int h=0; h<(growthHeight-1); h++) {
						Xie.setBlockMeta(world, i, j+h, k, maxGrowth);
						attemptToGrowHigher(world, i, j+h, k);
					}
					Xie.setBlockMeta(world, i, j+growthHeight-1, k, maxGrowth);
				}
			} else Xie.setBlockMeta(world, i, j, k, maxGrowth);
		}
	}

    @Override
    public void updateTick(World world, int i, int j, int k, Random random) {
        // super.updateTick(world, i, j, k, random);
	    testThatBlockCanStillGrowHere(world, i, j, k);
    	
	    int netGrowthRate = (int)(100F / growthRate);
	    if (netGrowthRate<=0) netGrowthRate=1;
	    
	    if(random.nextInt(netGrowthRate) == 0)
        {
            int growth = getPlantGrowth(world, i, j, k) + 1;
            if(growth < maxGrowth)
            {
            	Xie.setBlockMeta(world, i, j, k, growth);
            } else
            {
        		Xie.setBlockMeta(world, i, j, k, maxGrowth);
        		tryToGrowMoah(world, i, j, k);
            }
       }
	    
	    setBlockBoundsBasedOnState(world, i, j, k);
	}
	
    @Override
    public void onBlockPlacedBy(World world, int i, int j, int k, EntityLiving entityliving) {
    	if (world.isRemote) return;
    	
    	setBlockBoundsBasedOnState(world,i,j,k);
    	
    	if (!isVineLike) {
    		super.onBlockPlacedBy(world, i, j, k, entityliving);
    		return;
    	}
    	
    	// TODO verify these orientations
        int l = MathHelper.floor_double((double)((entityliving.rotationYaw * 4F) / 360F) + 0.5D) & 3;
        if(l == 0)
        {
            Xie.setBlockMeta(world, i, j, k, 2);
        }
        if(l == 1)
        {
            Xie.setBlockMeta(world, i, j, k, 1);
        }
        if(l == 2)
        {
            Xie.setBlockMeta(world, i, j, k, 3);
        }
        if(l == 3)
        {
            Xie.setBlockMeta(world, i, j, k, 0);
        }
    }
   
   // attempts to grow higher, spawn more of its kind, or turn into a different block, depending
   protected void tryToGrowMoah(World world, int i, int j, int k) {
	   if (reproductive && world.rand.nextInt(100)<reproductionRate) attemptToSpawn(world, i, j, k);
	   if (growthHeight>1) attemptToGrowHigher(world, i, j, k);
	   if (growsInto>0) growIntoSomethingElse(world, i, j, k);
   }
   
   protected void growIntoSomethingElse(World world, int i, int j, int k) {
	   int meta = 0; // default
	   if (inheritMeta) meta = world.getBlockMetadata(i, j, k);
	   if (Block.blocksList[growsInto] instanceof XieBlockLeaves) {
		   world.setBlock(i, j, k, 0);
		   if (!((XieBlockLeaves) Block.blocksList[growsInto]).generate(world, i, j, k, meta, false)) {
			  // failed to grow tree, replace sapling
			   world.setBlockAndMetadata(i, j, k, blockID, meta);
		   }
	   } else {
		   Xie.setBlockAndMeta(world, i, j, k, growsInto, meta);
	   }
   }
   
   // attempts to spawn a newly grown block at i,j+1,k. Returns true if its grown all that it can.
   protected void attemptToGrowHigher(World world, int i, int j, int k) {
	   if (getHeight(world, i, j, k)>=growthHeight) return;
	   if (world.getBlockId(i, j+1, k)==0) {
		   Xie.setBlockAndMeta(world, i, j+1, k, blockID, 0);
	   }
   }
   
   // attempts to spawn more of its ilk, like mushrooms (edited copy-pasta from BlockMushroom)
   protected void attemptToSpawn(World world, int i, int j, int k) {
	   Random random = world.rand;
	   for (int it = 0; it<3; it++) { // make 3 attempts at trying to spawn a new crop in an adjacent block
		   int x = (i + random.nextInt(3)) - 1;
	       int y = (j + random.nextInt(3)) - 1;
	       int z = (k + random.nextInt(3)) - 1;
	       if(world.isAirBlock(x, y, z) && canBlockStay(world, x, y, z))
	       {
	           Xie.setBlockAndMeta(world, x, y, z, blockID, 0);
	           return;
	       }
	   }
   }
   
   protected int getHeight(World world, int i, int j, int k) {
	   int count = 0;
	   while (world.getBlockId(i, j-count, k)==blockID) count++;
	   return count;
   }
   
   public int getPlantGrowth(World world, int i, int j, int k) {
	   if (isVineLike) return world.getBlockMetadata(i, j, k) & 3; // bitmask for the last 2 binary digits only
	   
	   return world.getBlockMetadata(i, j, k);
   }
   	
	public void setFlammability(int i, int j) {
		Xie.setFlammability(blockID, i, j);
	}
	
	public boolean setFlammability(String s) {
		s = s.toLowerCase();
		if (s.equals("wood")) {
			setFlammability(5,5);
			return true;
		} else if (s.equals("planks")) {
			setFlammability(5,20);
			return true;
		} else if (s.equals("leaves")) {
			setFlammability(30,60);
			return true;
		} else if (s.equals("tall grass") || s.equals("tallgrass")) {
			setFlammability(60, 100);
			return true;
		} else if (s.equals("cloth") || s.equals("wool")) {
			setFlammability(30, 60);
			return true;
		} else return false;
	}
	
	// static methods
	public static XieBlockPlant get(int blockID) {
		Iterator<XieBlockPlant> it = plants.iterator();
		while (it.hasNext()) {
			XieBlockPlant current = it.next();
			if (current.blockID==blockID) return current;
		}
		return null;
	}
	
	// Copy-pasta from XieBlock
	
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
	public boolean isOpaqueCube() {
    	if (isOpaque==null) isOpaque = true;
        return isOpaque;
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
    	return tex.getTexFromSideAndMeta(i, j);
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
	public void dropBlockAsItemWithChance(World world, int i, int j, int k, int l, float f, int multiplier) {
        if (world.isRemote) return;
    	
    	if (drops==null) {
        	// no drops defined, drop self if required
        	if (dropBlock) {
        		if (inheritMeta) Xie.spawnItem(new ItemStack(this,1,l), world, i, j, k); 
        		else Xie.spawnItem(new ItemStack(this), world, i, j, k);
        	}
        	return;
        } else drops.dropBlockAsItemWithChance(world, i, j, k, l, f, multiplier);
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
}
