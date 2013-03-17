package mod.xie.xm.blocks;

public enum BlockSide {
	ALL(-1), ENDS(-2), SIDES(-3),
	
	BOTTOM(0), 
	TOP(1), 
	LEFT(2), EAST(2),
	RIGHT(3), WEST(3),
	FRONT(5), NORTH(5), 
	BACK(6), SOUTH(6);
	
	private int value;
	
	private BlockSide(int i) {
		value = i;
	}

}
