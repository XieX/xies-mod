package mod.xie.xm.util;


// texture id handler for complex objects

public class XieTex {
	
	// 30 is a pink square as of Beta 1.8.1, random arbitrary default fail texture
	public static final int failTex = 31;
	
	// array [sides][metadata]
	protected int tex [][];
	protected int S=6;
	protected int M=16;
	protected int base;
	protected boolean simple;
	private String texFile; // path to texture file for 'forge based texture replacement
	
	public XieTex() {
		this(failTex);
	}
	
	public XieTex (int baseTex) {
		base = baseTex;
		simple = true;
	}
	
	public XieTex (int baseTex, boolean simpleTexture) {
		base = baseTex;
		simple = simpleTexture;
		if (!simple) fillTex(baseTex);
	}
	
	protected void fillTex(int baseTex) {
		if (tex==null) tex = new int[S][M];
		for (int i=0; i<S; i++) {
			for (int j=0; j<M; j++) {
				tex[i][j] = baseTex;
			}
		}
		simple=false;
	}
	
	public int getTexFromSide(int side) {
		return getTexFromSideAndMeta(side,0);
	}
	
	public int getTexFromSideAndMeta(int side, int meta) {
		if (simple) return base;
		if (side==SIDES) side=EAST;
		if (side<0) side=0; if (meta<0) meta=0;
		if (side>=S) side=S-1; if (meta>=M) meta=M-1;
		return tex[side][meta];
	}
	
	public boolean setTexture(int texID, String sides, String metas) {
		if (simple) { fillTex(base); simple=false; }
		
		sides=sides.toLowerCase();
		if (sides.equals("all")) {
			return setTexture(texID, new int[] {TOP, BOT, EAST, WEST, NORTH, SOUTH}, metas);
		} else if (sides.equals("sides")) {
			return setTexture(texID, new int[] {EAST, WEST, NORTH, SOUTH}, metas);
		} else if (sides.equals("ends")) {
			return setTexture(texID, new int[] {TOP, BOT}, metas);
		} else {
			boolean r = false;
			if (sides.contains("top")) r = r || setTexture(texID, TOP, metas);
			if (sides.contains("bot")) r = r || setTexture(texID, BOT, metas);
			if (sides.contains("north")) r = r || setTexture(texID, EAST, metas);
			if (sides.contains("south")) r = r || setTexture(texID, WEST, metas);
			if (sides.contains("east")) r = r || setTexture(texID, NORTH, metas);
			if (sides.contains("west")) r = r || setTexture(texID, SOUTH, metas);
			
			return r;
		}
	}
	
	public boolean setTexture(int texID, int sides [], String metas) {
		boolean r = false;
		for (int i=0; i<sides.length; i++) {
			r = r | setTexture(texID, sides[i], metas);
		}
		return r;
	}
	
	public boolean setTexture(int texID, int sides[], int metas[]) {
		for (int i=0; i<sides.length; i++) {
			for (int j=0; j<metas.length; j++) {
				setTexture(texID, sides[i],metas[j]);
			}
		}
		return true;
	}
	
	public boolean setTexture(int texID, int side, String metas) {
		metas = metas.toLowerCase();
		if (metas.equals("all")) return setTexture(texID, side);
		
		int metaRange[] = Xie.parseMetaRange(metas);
		if (metaRange==null) return false;
		else {
			boolean flag = true;
			
			int x = validate(metaRange[0], M);
			int y = validate(metaRange[1], M);
			
			for (int i=x; i<=y; i++) {
				flag = flag && setTexture(texID, side, i);
			}
			
			return flag;
		}
	}

	public int getTexFromAlias(String alias) {
		if (simple) return base;
		
		int side = getSide(alias);
		if (side==FAIL) return -1;
		
		if (tex[side]==null) return -1;
		else return tex[side][0];
	}
	
	public boolean setTexture(int texID, int side) {
		boolean flag = true;
		for (int i=0; i<M; i++) {
			flag = flag && setTexture(texID, side, i);
		}
		return flag;
	}
	
	public boolean setTexture(int texID, int side, int meta) {
		if (tex==null) fillTex(base);
		if (meta<0 || meta>=M) return false;
		
		if (side==SIDES) {
			for (int i=EAST; i<=SOUTH; i++) setTexture(texID, i, meta);
		} else if (side==ALL) {
			for (int i=0; i<S; i++) setTexture(texID, i, meta);
		} else if (side==ENDS) {
			setTexture(texID, TOP, meta);
			setTexture(texID, BOT, meta);
		} else if (side>=0 && side<S) {
			tex[side][meta]=texID;
		} else return false;
		return true;
	}
	
	private int validate(int num, int max) {
		if (num<0) return 0;
		if (num>max) return max;
		else return num;
	}
	
	// sides constants
	public static final int BOT = 0;
	public static final int TOP = 1;
	public static final int EAST = 2;
	public static final int LEFT = 2;
	public static final int WEST = 3;
	public static final int RIGHT = 3;
	public static final int NORTH = 4;
	public static final int FRONT = 4;
	public static final int SOUTH = 5;
	public static final int BACK = 5;
	
	public static final int ALL = -1;
	public static final int SIDES = -2;
	public static final int ENDS = -3;
	public static final int FAIL = -4;
	
	public static boolean isEnd(int side) {
		return side==-3 || side==0 || side==1;
	}
	
	public static boolean isSide(int side) {
		return side==-2 || (side>=2 && side<=5);
	}
	
	public static int getSide(String s) {
		if (s.toLowerCase().equals("top")) return TOP;
		else if (s.toLowerCase().equals("bottom")) return BOT;
		else if (s.toLowerCase().equals("north")) return NORTH;
		else if (s.toLowerCase().equals("south")) return SOUTH;
		else if (s.toLowerCase().equals("east")) return EAST;
		else if (s.toLowerCase().equals("west")) return WEST;
		else if (s.toLowerCase().equals("all")) return ALL;
		else if (s.toLowerCase().equals("sides")) return SIDES;
		else if (s.toLowerCase().equals("ends")) return ENDS;
		else if (s.toLowerCase().equals("front")) return NORTH;
		else if (s.toLowerCase().equals("back")) return SOUTH;
		else if (s.toLowerCase().equals("left")) return EAST;
		else if (s.toLowerCase().equals("right")) return WEST;
		else return FAIL;
	}
	
	public static String getSideStr(int s) {
		switch (s) {
		case BOT: return "bottom";
		case TOP: return "top";
		case EAST: return "east";
		case WEST: return "west";
		case NORTH: return "north";
		case SOUTH: return "south";
		case ALL: return "all";
		case SIDES: return "sides";
		case ENDS: return "ends";
		case FAIL: return "fail";
		default: return "unknown";
		}
	}

	@Override
	public String toString() {
		if (simple) return "("+base+")";
		else {
			if (tex==null) return "[null]";
			String rtn = "[";
			for (int i=0; i<S; i++) {
				for (int j=0; j<M; j++) {
					rtn+=tex[i][j]+" ";
				}
				rtn+="\n";
			}
			rtn+="]";
			return rtn;
		}
	}

	public String getTexFile() {
		if (texFile==null) {
			return null;
		}
		else return texFile;
	}

	public void setTexFile(String text) {
		texFile=text;
	}
}
