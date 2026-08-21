package com.example.examplemod.fauna.client;

import com.example.examplemod.FCGeoRenderer;
import com.example.examplemod.fauna.FaunaEntity;
import com.example.examplemod.fauna.entity.CatachanDevilEntity;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.core.object.Color;

/**
 * O renderer da fauna: o {@link FCGeoRenderer} generico mais as duas coisas visuais que dependem do
 * estado do bicho.
 *
 * <h2>Enterrado: nao desenha, e nem a sombra</h2>
 *
 * O corte e em {@link #shouldRender} e nao em {@code render}, e a diferenca importa: a sombra do bicho
 * e desenhada pelo {@code EntityRenderDispatcher} <b>antes</b> de chamar o renderer, entao cortar
 * dentro do {@code render} deixaria uma mancha de sombra circular no chao exatamente sobre o bicho
 * escondido. {@code shouldRender} false pula a entidade inteira, sombra incluida.
 *
 * <p>Transparencia foi descartada por dois motivos: ainda desenha (e ainda custa) um modelo de
 * duzentos cubos, e um bicho meio visivel sob o chao le como falha grafica em vez de emboscada.
 *
 * <p>Isto e seguro do lado do servidor: {@code isBurrowed} viaja por {@code SynchedEntityData}, entao
 * os dois lados concordam. Sem sincronizar, o cliente desenharia um bicho que o servidor considera
 * enterrado — o pior tipo de dessincronizacao, porque o jogador atacaria algo que nao esta la.
 *
 * <h2>Camuflado: mais escuro, nunca invisivel</h2>
 *
 * {@link #getRenderColor} multiplica a cor por 0,62 quando o Catachan Devil esta na pose de
 * camuflagem. Isso encosta o bicho na sombra da vegetacao sem apagar a silhueta dele: quem olhar com
 * atencao vai ve-lo, e e disso que o briefing fala quando proibe invisibilidade completa. Alfa fica
 * intacto de proposito — reduzir alfa exigiria trocar o render type e ainda faria o modelo ficar
 * visivel atraves de si mesmo.
 */
public class FaunaGeoRenderer<T extends FaunaEntity> extends FCGeoRenderer<T> {

    /** Quanto a camuflagem escurece. Multiplicador, nao subtracao: preserva o contraste da textura. */
    private static final float CAMOUFLAGE_DIM = 0.62F;

    public FaunaGeoRenderer(EntityRendererProvider.Context context, String name, float shadowRadius) {
        super(context, name, shadowRadius);
    }

    @Override
    public boolean shouldRender(T entity, Frustum frustum, double camX, double camY, double camZ) {
        if (entity.isBurrowed()) {
            return false;
        }

        return super.shouldRender(entity, frustum, camX, camY, camZ);
    }

    @Override
    public Color getRenderColor(T animatable, float partialTick, int packedLight) {
        if (animatable instanceof CatachanDevilEntity devil && devil.isCamouflaged()) {
            return Color.ofRGB(CAMOUFLAGE_DIM, CAMOUFLAGE_DIM, CAMOUFLAGE_DIM);
        }

        return Color.WHITE;
    }
}
