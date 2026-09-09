package dev.shadowsoffire.placebo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagEntry;

/**
 * Exposes {@link TagEntry}'s private {@code id}/{@code tag} fields publicly.
 * <p>
 * NeoForge patches these as public getters ({@code isTag()}/{@code getId()}) directly on
 * vanilla's {@code TagEntry}; plain Fabric has no such patch, so this widens access via
 * mixin instead — used by {@link dev.shadowsoffire.placebo.dynreg.tag.TagLoader}.
 */
@Mixin(TagEntry.class)
public interface TagEntryAccessor {

    @Accessor("id")
    Identifier placebo$getId();

    @Accessor("tag")
    boolean placebo$isTag();

}
