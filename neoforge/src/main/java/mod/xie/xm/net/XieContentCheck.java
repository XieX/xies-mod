package mod.xie.xm.net;

import mod.xie.xm.XiesMod;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Refuses a connection between a client and a server whose content differs, and
 * says which packs differ.
 *
 * <p>Both sides must load the same content, because blocks and items are registered
 * from it. Without this check a server with a block the client lacks fails registry
 * sync with a list of unknown ids, and a client with extra content, or the same ids
 * with different properties, connects and then disagrees with the server about what
 * it sees.
 *
 * <p>The check is a NeoForge channel whose version is {@link XiesMod#contentVersion},
 * each pack's name and fingerprint. NeoForge negotiates channel versions before it
 * syncs registries and refuses a mismatch on its mod mismatch screen, which shows
 * the client's version and the server's side by side. So nothing is ever sent on
 * the channel: it exists to be negotiated.
 */
public final class XieContentCheck {

    private XieContentCheck() { }

    /** The payload of the channel, which is registered and never sent. */
    public static final class Unused implements CustomPacketPayload {

        static final Type<Unused> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(XiesMod.MOD_ID, "content"));

        static final Unused INSTANCE = new Unused();

        private Unused() { }

        @Override
        public Type<Unused> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(XiesMod.contentVersion())
            .configurationToServer(Unused.TYPE, StreamCodec.unit(Unused.INSTANCE), (payload, context) -> { });
    }
}
