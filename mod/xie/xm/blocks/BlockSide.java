package mod.xie.xm.blocks;

import mod.xie.xm.file.XieLog;

public enum BlockSide {
	ALL(-1), ENDS(-2), SIDES(-3),
	
	BOTTOM(0), 
	TOP(1), 
	LEFT(5), EAST(5),
	RIGHT(4), WEST(4),
	FRONT(2), NORTH(2), 
	BACK(3), SOUTH(3);
	
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
	
	public int value() {
		return value;
	}

}
