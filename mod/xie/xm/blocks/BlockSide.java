package mod.xie.xm.blocks;

import mod.xie.xm.file.XieLog;

public enum BlockSide {
	ALL(-1), ENDS(-2), SIDES(-3),
	
	// TODO FTFESAD check these numbers
	BOTTOM(0), 
	TOP(1), 
	LEFT(2), EAST(2),
	RIGHT(3), WEST(3),
	FRONT(4), NORTH(4), 
	BACK(5), SOUTH(5);
	
	private int value;
	
	private BlockSide(int i) {
		value = i;
	}
	
	public static BlockSide parse(String sideName) {
		for (BlockSide side : BlockSide.values()) {
			if (sideName.equalsIgnoreCase(side.name())) return side;
		}
		
		// first pass failed, loosen up search...
		for (BlockSide side : BlockSide.values()) {
			if (sideName.toUpperCase().contains(side.name().toUpperCase())) return side;
		}
		
		// else
		XieLog.log().logFailure("Failed to parse side "+sideName);
		return null;
	}

}
