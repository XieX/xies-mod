package mod.xie.xm.tex;

import java.util.HashMap;
import java.util.Map.Entry;

import mod.xie.xm.file.XieLog;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.util.Icon;

public class XieTexMetaVariable extends XieTex {

	// assumes supported meta values are continuous, or at least not sparse, 
	// otherwise this object is not very memory efficient
	
	private Icon[] textures;
	private Icon defaultTexture;
	
	public XieTexMetaVariable(IconRegister registrar, String[] textureFiles) {
		super(registrar);
		textures = parseMetavariantTextures(textureFiles);
	}

	public XieTexMetaVariable(IconRegister registrar, String[] textureFiles, String defaultTextureName) {
		this(registrar, textureFiles);
		defaultTexture = register(defaultTextureName);
	}

	@Override
	public Icon getIcon(int i, int meta) {
		if (meta<textures.length && textures[meta]!=null) {
			return textures[meta];
		} else if (defaultTexture!=null) return defaultTexture;
		else return textures[textures.length-1];
	}

	@Override
	public Icon getIconFromDamage(int meta) {
		if (meta<textures.length && textures[meta]!=null) {
			return textures[meta];
		} else if (defaultTexture!=null) return defaultTexture;
		else return textures[0];
	}

	@Override
	public XieTexType getType() {
		return XieTexType.META_VARIABLE;
	}
	
	protected Icon[] parseMetavariantTextures(String[] textureFiles) {
		HashMap<Integer,Icon> texMap = new HashMap<Integer,Icon>();
		
		for (int i=0; i < textureFiles.length; i++) {
			int meta = parseMetavalue(textureFiles[i]);
			if (meta>=0) {
				Icon tex = register(textureFiles[i]);
				texMap.put(meta,tex);
			} else XieLog.log().logIncomplete("Texture filename "+textureFiles[i]+" confused me, so I'm ignoring it. Sorry :3");
		}
		
		if (texMap.isEmpty()) {
			XieLog.log().logFailure("Failed to correctly parse these supposed meta-indicative files, falling back to simple tex using: "+textureFiles[0]);
			texMap.put(0, register(textureFiles[0]));
		}
		
		int maxMeta = 0;
		for (Integer i : texMap.keySet()) if (i>maxMeta) maxMeta = i;
		Icon[] returnTextures = new Icon[maxMeta+1];
		for (Integer i : texMap.keySet()) returnTextures[i] = texMap.get(i);
		
		return returnTextures;
	}

	public int getMaxMeta() {
		if (textures==null) return super.getMaxMeta();
		return textures.length;
	}
}
