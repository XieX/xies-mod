package mod.xie.xm.blocks;

import mod.xie.xm.util.ItemWrapper;
import mod.xie.xm.util.Xie;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.src.ModLoader;
import net.minecraft.tileentity.TileEntityFurnace;

import net.minecraft.block.Block;
import net.minecraft.block.StepSound;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;


public class XieTileEntityFurnace extends TileEntityFurnace {
	
	public XieBlockFurnace block;
	
	protected ItemStack content[];
	protected int inventoryStackLimit = 64;
	
	// constructors
	public XieTileEntityFurnace() {
		super();
        content = new ItemStack[3];
	}
	
	// getters and setters
	public void setStackLimit(int s) { inventoryStackLimit=s; }
	public ItemStack[] inventory() { return content; }
	
	// new methods
	// TODO this is really dodgy, there has to be a better way 
	protected void validateBlockObject() {
		if (block!=null) return;
		
		try {
			block = (XieBlockFurnace) Block.blocksList[worldObj.getBlockId(xCoord, yCoord, zCoord)];
		} catch (ClassCastException e) {
			for (Block b : Block.blocksList) if (b instanceof XieBlockFurnace) block = (XieBlockFurnace)b;
		} catch (NullPointerException e) {
			for (Block b : Block.blocksList) if (b instanceof XieBlockFurnace) block = (XieBlockFurnace)b;
		}
		furnaceBurnTime = block.initialBurnTime;
	}
	
    protected ItemStack getSmeltResult() {
    	validateBlockObject();
    	
    	if(content[0] == null) return null;
    	
        ItemStack smeltable = content[0];
        ItemStack result = null;
        
        // get vanilla smelting result if applicable
        if (block.smeltsAsFurnace) result = FurnaceRecipes.smelting().getSmeltingResult(smeltable.itemID);
        
        // get custom smelting result if applicable
        if (!block.smeltables.isEmpty())
        	for (ItemWrapper key : block.smeltables.keySet()) {
        		if (key.equals(smeltable)) result = block.smeltables.get(key).getItemStack();
        	}
        
        return result;
    }
	
	// overridden superclass methods    
    public void updateEntity() {
    	validateBlockObject();
    	
        boolean wasBurning = furnaceBurnTime > 0;
        boolean inventoryChange = false;

        if(furnaceBurnTime > 0) furnaceBurnTime--;

        if(!worldObj.isRemote) {
            if(furnaceBurnTime == 0 && (canSmelt() || block.burnsOut())) {
                currentItemBurnTime = furnaceBurnTime = getBurnTime(content[1]);
                
                if(furnaceBurnTime > 0) {
                    inventoryChange = true;
                    if(content[1] != null) {
                        if(content[1].getItem().hasContainerItem())
                            content[1] = new ItemStack(content[1].getItem().getContainerItem());
                        else content[1].stackSize--;

                        if(content[1].stackSize == 0) content[1] = null;
                    }
                }
            }
            
            if(isBurning() && canSmelt()) {
                furnaceCookTime++;
                if(furnaceCookTime == block.getRequiredCookTime()) {
                    furnaceCookTime = 0;
                    smeltItem();
                    inventoryChange = true;
                }
            } else furnaceCookTime = 0;
            
            int burnLevel = furnaceBurnTime;
            if (content[1]!=null) {
            	if (burnLevel<getBurnTime(content[1])) burnLevel = getBurnTime(content[1]);
            	if (burnLevel==0) burnLevel = furnaceBurnTime;
            }
            
            if(wasBurning != (furnaceBurnTime > 0)) {
                inventoryChange = true;
                block.updateState(burnLevel, worldObj, xCoord, yCoord, zCoord);
            }
            
            if (block.metaVariesByBurnTime())
            	block.updateState(burnLevel, worldObj, xCoord, yCoord, zCoord);
        }
        
        if(inventoryChange) onInventoryChanged();
    }
	
	public String getInvName() {
    	validateBlockObject();
    	
        return block.getDisplayName();
    }
    
    public int getInventoryStackLimit()
    {
        return inventoryStackLimit;
    }
    
    protected boolean canSmelt()
    {
    	ItemStack result = getSmeltResult();
    	if (result==null) return false;
        
        // check that receiving inventory slot is empty or compatible
        if(content[2] == null) return true;
        if(!content[2].isItemEqual(result)) return false;
        int stacksize = content[2].stackSize + result.stackSize;
        return (stacksize<=getInventoryStackLimit() && stacksize<=result.getMaxStackSize());
    }
    
    @Override
    public void smeltItem() {    	
        ItemStack result = getSmeltResult();
        if (result==null) return;
        
        if(content[2] == null) content[2] = result.copy();  
        else if(content[2].isItemEqual(result)) content[2].stackSize+=result.stackSize;
        
        if(content[0].getItem().hasContainerItem()) 
        	content[0] = new ItemStack(content[0].getItem().getContainerItem());
        else content[0].stackSize--;

        if(content[0].stackSize <= 0) content[0] = null;
    }

    public int getBurnTime(ItemStack itemstack) {
    	validateBlockObject();
    	
        if(itemstack == null) return 0;
        
        for (ItemWrapper key : block.fuels.keySet())
        	if (key.equals(itemstack)) return block.fuels.get(key);
        
        if (block.fuelsAsFurnace) return getFurnaceBurnTime(itemstack);
        else return 0;
    }

    // Copied (because furnaceItemStacks in super class is private
    public ItemStack getStackInSlot(int i)
    {
        return content[i];
    }

    @Override
    public ItemStack decrStackSize(int i, int j)
    {
        if(content[i] != null)
        {
            if(content[i].stackSize <= j)
            {
                ItemStack itemstack = content[i];
                content[i] = null;
                return itemstack;
            }
            ItemStack itemstack1 = content[i].splitStack(j);
            if(content[i].stackSize == 0)
            {
                content[i] = null;
            }
            return itemstack1;
        } else
        {
            return null;
        }
    }

    @Override
    public void setInventorySlotContents(int i, ItemStack itemstack)
    {    	
        content[i] = itemstack;
        
        if(itemstack != null && itemstack.stackSize > getInventoryStackLimit())
        {
            itemstack.stackSize = getInventoryStackLimit();
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound)
    {
        super.readFromNBT(nbttagcompound);
        NBTTagList nbttaglist = nbttagcompound.getTagList("Items");
        content = new ItemStack[getSizeInventory()];
        for(int i = 0; i < nbttaglist.tagCount(); i++)
        {
            NBTTagCompound nbttagcompound1 = (NBTTagCompound)nbttaglist.tagAt(i);
            byte byte0 = nbttagcompound1.getByte("Slot");
            if(byte0 >= 0 && byte0 < content.length)
            {
                content[byte0] = ItemStack.loadItemStackFromNBT(nbttagcompound1);
            }
        }

        furnaceBurnTime = nbttagcompound.getShort("BurnTime");
        furnaceCookTime = nbttagcompound.getShort("CookTime");
        currentItemBurnTime = getBurnTime(content[1]);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound)
    {
        super.writeToNBT(nbttagcompound);
        nbttagcompound.setShort("BurnTime", (short)furnaceBurnTime);
        nbttagcompound.setShort("CookTime", (short)furnaceCookTime);
        NBTTagList nbttaglist = new NBTTagList();
        for(int i = 0; i < content.length; i++)
        {
            if(content[i] != null)
            {
                NBTTagCompound nbttagcompound1 = new NBTTagCompound();
                nbttagcompound1.setByte("Slot", (byte)i);
                content[i].writeToNBT(nbttagcompound1);
                nbttaglist.appendTag(nbttagcompound1);
            }
        }

        nbttagcompound.setTag("Items", nbttaglist);
    }

    public int getFurnaceBurnTime(ItemStack itemstack) {
        if(itemstack == null) return 0;
        int i = itemstack.getItem().itemID;
        if(i < Xie.idShift && Block.blocksList[i].blockMaterial == Material.wood) return 300;
        if(i == Item.stick.itemID)  return 100;
        if(i == Item.coal.itemID) return 1600;
        if(i == Item.bucketLava.itemID) return 20000;
        if(i == Block.sapling.blockID) return 100;
        else return ModLoader.addAllFuel(i, itemstack.getItemDamage());
    }

    @Override
    public int getCookProgressScaled(int i)
    {
        return (furnaceCookTime * i) / block.getRequiredCookTime();
    }

    @Override
    public int getBurnTimeRemainingScaled(int i)
    {
    	validateBlockObject();
    	
        if(currentItemBurnTime == 0)
        {
            currentItemBurnTime = block.getBurnLevelScaleMax();
        }
        return (furnaceBurnTime * i) / currentItemBurnTime;
    }
}
