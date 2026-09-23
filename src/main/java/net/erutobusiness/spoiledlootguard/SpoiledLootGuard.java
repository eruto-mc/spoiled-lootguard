package net.erutobusiness.spoiledlootguard;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 未開封の入れ物を、腐敗の巡回から外す。
 *
 * <p>仕組みと経緯は {@link net.erutobusiness.spoiledlootguard.mixin.SpoilHandlerMixin} に書いた。
 * このクラスが持つのは<b>記録だけ</b>。
 *
 * <p><b>なぜ記録が要るか。</b> 2026-09-23 の凍結を調べたとき、
 * 「未開封のトロッコが触られた」ことは spark の記録から分かったのに、
 * ⚠ <b>それがどこの、どのトロッコだったのかを答える手段が1つも無かった</b>。
 * サーバのログに部員の位置は残らず、チャンクの読み込みも記録されない。
 * そのため「何が新しい土地を読み込ませたのか」を最後まで特定できなかった。
 * ⚠ <b>次に何か起きたとき、1行で場所が分かるようにしておく。</b>
 */
@Mod(SpoiledLootGuard.MOD_ID)
public class SpoiledLootGuard {

    public static final String MOD_ID = "spoiledlootguard";

    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * 記録の間隔。
     *
     * <p>⚠ <b>1件ごとに書かない。</b> 部員が新しい土地へ入ると一度に何台も外れるので、
     * 毎回書くとログが埋まる。⚠ かといって「初回のみ」だと<b>2回目以降の嵐の場所が分からない</b>
     * （当部の {@code holding_preserves} は効果の確認が目的なので初回のみでよい。こちらは用途が違う）。
     * 10分空いたら次の1行を書く＝<b>嵐1回につき1行</b>になる。
     */
    private static final long QUIET_MS = 10L * 60L * 1000L;

    /** 通算で外した件数。⚠ サーバスレッドからしか触られない（{@code onWorldTick} の中）ので同期しない。 */
    private static long skipped = 0L;

    /** 最後に1行書いた時刻。0 なら一度も書いていない。 */
    private static long reportedAt = 0L;

    /** 最後に1行書いた時点の通算件数。 */
    private static long reportedCount = 0L;

    public SpoiledLootGuard() {
    }

    /**
     * 1台外したことを控える。必要なら1行書く。
     *
     * <p>⚠ <b>毎tick・何台ぶんも呼ばれる。</b> 通常は足し算と引き算1回で戻る。
     */
    public static void noteSkip(Level level, Entity entity) {
        skipped++;
        long now = System.currentTimeMillis();
        if (reportedAt != 0L && now - reportedAt < QUIET_MS) {
            return;
        }
        long since = skipped - reportedCount;
        reportedAt = now;
        reportedCount = skipped;
        BlockPos pos = entity.blockPosition();
        LOGGER.info("未開封の入れ物を腐敗の巡回から外した: {} @ {} [{}, {}, {}]"
                + "（前回の記録から {} 件 ／ 通算 {} 件）",
            entity.getType(), level.dimension().location(),
            pos.getX(), pos.getY(), pos.getZ(), since, skipped);
    }
}
