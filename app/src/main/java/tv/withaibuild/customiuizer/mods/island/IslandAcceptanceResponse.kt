package tv.withaibuild.customiuizer.mods.island

import kotlin.math.abs

/** 22G100 Up acceptance: +16/+26 and centerY -4; .12s to rebound,
 * another .08s to Accepted. One caller-owned finite clock advances all axes.
 * A14's seed-to-shell layout is separate from the receive excursion. Switching
 * the large seed expansion to Accepted's low damping excites a second bounce
 * which is not justified by the recovered existing-container consumer. The
 * native first-music routing and complete solver remain unverified. */
internal class IslandAcceptanceResponse(fromWidth: Float,fromHeight: Float,
    private val targetWidth: Float,private val targetHeight: Float,
    velocityWidth: Float,velocityHeight: Float,density: Float,
    private val minimumWidth: Float,private val maximumWidth: Float,
    private val minimumHeight: Float,private val maximumHeight: Float) {
    private val stretch=IslandMotion.Profile(.5,.714)
    private val rebound=IslandMotion.Profile(.35,1.0)
    private val accepted=IslandMotion.Profile(.513,.525)
    private val acceptedY=IslandMotion.Profile(.513,1.0)
    private val baseWidth=IslandMotion.Spring().apply { setVelocity(velocityWidth); retarget(fromWidth,targetWidth,IslandMotion.IOS_BOUNDS,true) }
    private val baseHeight=IslandMotion.Spring().apply { setVelocity(velocityHeight); retarget(fromHeight,targetHeight,acceptedY,true) }
    private val extraWidth=IslandMotion.Spring().apply { retarget(0f,16f*density,stretch,false) }
    private val extraHeight=IslandMotion.Spring().apply { retarget(0f,26f*density,stretch,false) }
    private val extraY=IslandMotion.Spring().apply { retarget(0f,-4f*density,stretch,false) }
    // A conservative Android solver budget, not a recovered iOS duration. The
    // owner may finish earlier only after the presented axes actually settle.
    val durationMillis = 200L + maxOf(
        IslandMotion.IOS_BOUNDS.horizon(fromWidth-targetWidth,velocityWidth),
        acceptedY.horizon(fromHeight-targetHeight,velocityHeight),
        accepted.horizon(16f*density,(16f*density*stretch.omega).toFloat()),
        acceptedY.horizon(26f*density,(26f*density*stretch.omega).toFloat()))
    private var stage=1
    var width=fromWidth; private set
    var height=fromHeight; private set
    var y=0f; private set
    var velocityWidth=velocityWidth; private set
    var velocityHeight=velocityHeight; private set
    var velocityY=0f; private set
    fun sample(time: Float) {
        if(stage==1 && time>=.12f) {
            for(axis in arrayOf(extraWidth,extraHeight,extraY)) axis.retarget(axis.sample(.12f),0f,rebound,true)
            stage=2
        }
        if(stage==2 && time>=.20f) {
            extraWidth.retarget(extraWidth.sample(.08f),0f,accepted,true)
            extraHeight.retarget(extraHeight.sample(.08f),0f,acceptedY,true)
            extraY.retarget(extraY.sample(.08f),0f,acceptedY,true)
            stage=3
        }
        val extraTime=if(stage>=3) (time-.20f).coerceAtLeast(0f) else if(stage>=2) (time-.12f).coerceAtLeast(0f) else time
        val baseW=baseWidth.sample(time)
        val extraW=extraWidth.sample(extraTime)
        // The receive excursion can recoil, but cannot invent a negative grip
        // beneath the independently progressing Android layout.
        val w=baseW+extraW.coerceAtLeast(0f)
        val h=baseHeight.sample(time)+extraHeight.sample(extraTime)
        width=w.coerceIn(minimumWidth,maximumWidth); height=h.coerceIn(minimumHeight,maximumHeight)
        velocityWidth=if(width==w) baseWidth.velocity+(if(extraW>=0f) extraWidth.velocity else 0f) else 0f
        velocityHeight=if(height==h) baseHeight.velocity+extraHeight.velocity else 0f
        y=extraY.sample(extraTime); velocityY=extraY.velocity
    }
    fun settled(time: Float)=time>=.20f && abs(width-targetWidth)<.25f && abs(height-targetHeight)<.25f &&
        abs(y)<.25f && abs(velocityWidth)<2f && abs(velocityHeight)<2f && abs(velocityY)<2f
}
