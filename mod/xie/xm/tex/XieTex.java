package mod.xie.xm.tex;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mod.xie.xm.Regexie;
import mod.xie.xm.file.XieLog;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.item.Item;
import net.minecraft.util.Icon;
import mod.xie.xm.blocks.BlockSide;

public abstract class XieTex {
	public abstract XieTexType getType();
	protected XieTexLink base;
	
	protected IconRegister iconRegister;
	
	protected XieTex(IconRegister registrar) {
		iconRegister = registrar;
	}
	
	public XieTex setTextureBase(Block textureBase) {
		if (textureBase!=null) base = new XieTexLink(iconRegister, textureBase);
		return this;
	}
	
	public XieTex setTextureBase(Item textureBase) {
		if (textureBase!=null) base = new XieTexLink(iconRegister, textureBase);
		return this;
	}
	
	protected Icon register(String str) {
		// TODO HACK this could me more elegant
		if (str.toLowerCase().endsWith(".png")) str = str.substring(0,str.length()-4);
		if (str.contains("/")) {
			str = str.substring(str.lastIndexOf("/"));
		}
		return iconRegister.registerIcon("xie:"+str);
	}
	
	protected int parseMetavalue(String filename) {
		Matcher matcher = Regexie.X_FILENAME.matcher(filename);
		if (!matcher.matches()) {
			XieLog.log().logFailure("Infernal Error: Could not parse meta value from filename "+filename);
			return -1;
		}

		String meta = matcher.group(Regexie.GROUP_META).replace("_", "");
		try {
			return Integer.parseInt(meta);
		} catch (NumberFormatException e) {
			XieLog.log().logFailure("NumberFormatException parsing meta value from filename: "+filename);
			return -1;
		}
	}
	
	protected BlockSide parseSideValue(String filename) {
		Matcher matcher = Regexie.X_FILENAME.matcher(filename);
		if (!matcher.matches()) {
			XieLog.log().logFailure("Infernal Error: Could not parse side value from filename "+filename);
			return BlockSide.ALL;
		}
		
		String sideName = matcher.group(Regexie.GROUP_SIDE).replace("_", "");
		return BlockSide.parse(sideName);
	}
	
	// defaults
	public Icon getIcon(int i, int j) {
		if (base!=null) return base.getIcon(i,j);
		else return null;
	}
	
	public Icon getIconFromDamage(int j) {
		if (base!=null) return base.getIconFromDamage(j);
		else return null;
	}

	public int getMaxMeta() {
		return 0;
	}
}
