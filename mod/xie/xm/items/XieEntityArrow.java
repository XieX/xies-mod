package mod.xie.xm.items;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

public class XieEntityArrow extends EntityArrow {

	protected Item item;
	
	public XieEntityArrow(World world, EntityPlayer player, float f, Item i) {
		super(world,player,f);
		item = i;
	}
	
	// new methods
	
	// very round-about getter method for the private super field "inGround"... :P
	protected boolean getIsInGround() {
		NBTTagCompound n = new NBTTagCompound();
		super.writeEntityToNBT(n);
		if (n.hasKey("inGround")) return n.getByte("inGround") == 1;
		else return false;
	}

	// overrides
	
	public void writeEntityToNBT(NBTTagCompound compound)
    {
        super.writeEntityToNBT(compound);
        
        compound.setInteger("itemId", item.shiftedIndex);
    }

    /**
     * (abstract) Protected helper method to read subclass entity data from NBT.
     */
    public void readEntityFromNBT(NBTTagCompound compound)
    {
        super.readEntityFromNBT(compound);
        
        int itemId = compound.getInteger("itemId");
        if (itemId>0 && itemId<Item.itemsList.length) {
        	if (Item.itemsList[itemId]!=null) item = Item.itemsList[itemId];
        }
        
        if (item==null) item=Item.arrow;
    }

    /**
     * Called by a player entity when they collide with an entity
     */
    public void onCollideWithPlayer(EntityPlayer player)
    {
        if (worldObj.isRemote) return;
        
        boolean inGround = getIsInGround();

        if (inGround && canBePickedUp==1 && arrowShake <= 0 && player.inventory.addItemStackToInventory(new ItemStack(item, 1)))
        {
            worldObj.playSoundAtEntity(this, "random.pop", 0.2F, ((rand.nextFloat() - rand.nextFloat()) * 0.7F + 1.0F) * 2.0F);
            player.onItemPickup(this, 1);
            setDead();
        }
    }
}
