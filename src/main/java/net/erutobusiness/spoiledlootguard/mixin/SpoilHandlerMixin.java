package net.erutobusiness.spoiledlootguard.mixin;

import com.mrbysco.spoiled.handler.SpoilHandler;
import net.erutobusiness.spoiledlootguard.SpoiledLootGuard;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 未開封のチェスト付きトロッコを、腐敗の巡回から外す。
 *
 * <p><b>Spoiled 本体が片側にだけ入れ忘れている除外</b>である。
 * {@code SpoilHandler#onWorldTick} は2種類を巡回する:
 *
 * <ul>
 *   <li><b>ブロック</b>（チェスト・樽など）… {@code RandomizableContainerBlockEntity} で
 *       {@code getLootTable() != null} のもの＝<b>未開封のダンジョンチェストを除外している</b></li>
 *   <li><b>エンティティ</b>（チェスト付きトロッコ・ボート）… {@code updateContainer} を呼ぶだけで、
 *       <b>同じ除外が無い</b></li>
 * </ul>
 *
 * <p>そのため未開封のトロッコの中身を1マス読みに行き、バニラの
 * {@code AbstractMinecartContainer#getItem} → {@code ContainerEntity#getChestVehicleItem}
 * → {@code unpackChestVehicleLootTable(null)} が走って、<b>誰も開けていないのに戦利品の抽選が実行される</b>。
 * 引数の Player は「誰が開けたか」の記録用で、コードから読んだときは null が入るだけなので止まらない。
 *
 * <p><b>なぜ重いか（2026-09-23 に実測）。</b> 廃坑のトロッコの戦利品表は
 * {@code minecraft:chests/abandoned_mineshaft}。Alex's Caves の {@code cabin_map} が
 * <b>この表だけを条件にして</b>地下小屋の地図を1枚足し、{@code MapItem#renderBiomePreviewMap} が
 * 地図のマスごとにバイオームを引く。小屋は遠くの未読み込みの土地にあるので
 * {@code LevelReader#getBiome} は読み込み済みチャンクから答えられず、
 * {@code MultiNoiseBiomeSource#getNoiseBiome} まで落ちて地形の数式を最初から計算する。
 * ⚠ <b>当ワールドは Tectonic の密度関数と citadel の差し込みを通るので1回が重い。</b>
 *
 * <p>⚠ <b>実測</b>: レンタルサーバが <b>5分10秒（310,653 ms）のあいだ tick を1回も進めず</b>、
 * 接続が3つ同時に切れた。spark の記録で、その5分の <b>89.3% がこの経路</b>
 * （{@code SpoilHandler} → {@code CabinMapLootModifier}）だった。
 * ⚠ 同じ日に「入れ物が溢れた」警告が <b>33 件</b>出ており、部員が新しい土地へ入るたび再発していた。
 *
 * <p>⚠ <b>Chunky で焼くほど増える。</b> 生成時に入るのは引換券（{@code LootTable}）だけで中身は無く、
 * 最初に触られたときに作られる。2週間誰も行っていない遠方の region を読んでも引換券が残っていた。
 * ⚠ <b>焼いた 213 万チャンクは、未開封のトロッコを世界中に敷き詰めたことでもある。</b>
 *
 * <p><b>塞ぐ場所</b>: {@code updateContainer} の入口。⚠ 逆アセンブルで<b>呼び出し元は1か所だけ</b>
 * （{@code onWorldTick} の offset 550）と確認済みなので、ここで止めれば漏れない。
 * ⚠ {@code who_patches.py} で当て先を数えたところ、{@code SpoilHandler} を触っているのは
 * 当部の {@code holding_preserves} 1本だけで、上書きの衝突は無い。
 *
 * <p>⚠ <b>腐敗の挙動は変わらない。</b> 未開封の入れ物には食べ物がまだ入っていないので、
 * 腐らせる対象がそもそも無い。開ければ引換券は消え、以後は普通に巡回される。
 *
 * <p>{@code remap = false}: 当て先は Spoiled 自身のメソッドで SRG 写像の対象外。
 */
@Mixin(value = SpoilHandler.class, remap = false)
public class SpoilHandlerMixin {

    @Inject(method = "updateContainer", at = @At("HEAD"), cancellable = true, remap = false)
    private void spoiledlootguard$skipUnopenedLoot(Level level, Entity entity, Container container,
                                                   CallbackInfo ci) {
        // ⚠ getLootTable() は引換券を読むだけで、中身の抽選は起こさない
        //    （抽選を起こすのは getItem / getChestVehicleItem のほう）。
        if (entity instanceof ContainerEntity vehicle && vehicle.getLootTable() != null) {
            SpoiledLootGuard.noteSkip(level, entity);
            ci.cancel();
        }
    }
}
