# Placebo (Fabric port)

A Fabric port of [Placebo](https://github.com/Shadows-of-Fire/Placebo) by
Shadows-of-Fire, the shared base library that [Apotheosis](https://github.com/Shadows-of-Fire/Apotheosis)
is built on. Built for the Gameoverse Minecraft server (Fabric 26.1.2) as a
dependency of our [Apotheosis Fabric port](https://github.com/WasabiIceCream/apotheosis-fabric)
— it isn't useful on its own without another mod that depends on it.

Ported only as far as needed to support that Apotheosis port, not a
complete 1:1 port of every Placebo feature. It covers dynamic registries
and datapack-driven content, networking, item/block-entity utilities, and
the attribute-modifier and enchantment-level hooks Apotheosis's affix/gem
system needs.

## License

MIT, same as upstream — see `LICENSE`.
