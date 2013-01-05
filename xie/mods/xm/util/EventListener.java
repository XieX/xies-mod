package xie.mods.xm.util;

import net.minecraft.world.World;
import net.minecraftforge.event.Event;
import net.minecraftforge.event.IEventListener;
import net.minecraftforge.event.entity.player.BonemealEvent;

public class EventListener implements IEventListener {

	@Override
	public void invoke(Event event) {
		if (event instanceof BonemealEvent) {
			BonemealEvent e = (BonemealEvent)event;
			doBonemeal(e.world, e.ID, e.X, e.Y, e.Z);
		}
	}
	
	public boolean doBonemeal(World world, int blockID, int X, int Y, int Z) {
		return BonemealHandler.doBonemeal(world,blockID,X,Y,Z);
	}
}
