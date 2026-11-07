package tv.withaibuild.customiuizer.mods.island

import kotlin.math.abs

/** 22G100 Up acceptance: +16/+26 and centerY -4; .12s to rebound,
 * another .08s to Accepted. One caller-owned finite clock advances all axes.
 * Width follows one retargeted body, rather than adding an early pulse to a
 * separate seed expansion. A14 keeps height independent so horizontal recoil
 * does not excite another vertical bounce. Native first-music routing and the
 * complete UIKit solver remain unverified. */
internal class IslandAcceptanceResponse(fromWidth: Float,fromHeight: Float,
    private val targetWidth: Float,private val targetHeight: Float,
    velocityWidth: Float,velocityHeight: Float,density: Float,
    minimumWidth: Float,maximumWidth: Float,
    private val minimumHeight: Float,private val maximumHeight: Float,
    heightScale: Float=1f) {
    private companion object {
        val stretch=IslandMotion.Profile(.5,.714)
        val rebound=IslandMotion.Profile(.35,1.0)
        val acceptedY=IslandMotion.Profile(.513,1.0)
    }
    val breadth=IslandArrivalBreadth(fromWidth,targetWidth,velocityWidth,density,minimumWidth,maximumWidth)
    private val baseHeight=IslandMotion.Spring().apply { setVelocity(velocityHeight); retarget(fromHeight,targetHeight,acceptedY,true) }
    private val heightStretch=26f*density*heightScale
    private val extraHeight=IslandMotion.Spring().apply { retarget(0f,heightStretch,stretch,false) }
    private val extraY=IslandMotion.Spring().apply { retarget(0f,-4f*density*heightScale,stretch,false) }
    // A conservative Android solver budget, not a recovered iOS duration. The
    // owner may finish earlier only after the presented axes actually settle.
    val durationMillis = maxOf(breadth.durationMillis,200L + maxOf(
        acceptedY.horizon(fromHeight-targetHeight,velocityHeight),
        acceptedY.horizon(heightStretch,(heightStretch*stretch.omega).toFloat())))
    private var stage=1
    val width get()=breadth.width
    var height=fromHeight; private set
    var y=0f; private set
    val velocityWidth get()=breadth.velocity
    var velocityHeight=velocityHeight; private set
    var velocityY=0f; private set
    fun sample(time: Float) {
        breadth.sample(time)
        if(stage==1 && time>=.12f) {
            for(axis in arrayOf(extraHeight,extraY)) axis.retarget(axis.sample(.12f),0f,rebound,true)
            stage=2
        }
        if(stage==2 && time>=.20f) {
            extraHeight.retarget(extraHeight.sample(.08f),0f,acceptedY,true)
            extraY.retarget(extraY.sample(.08f),0f,acceptedY,true)
            stage=3
        }
        val extraTime=if(stage>=3) (time-.20f).coerceAtLeast(0f) else if(stage>=2) (time-.12f).coerceAtLeast(0f) else time
        val h=baseHeight.sample(time)+extraHeight.sample(extraTime)
        height=h.coerceIn(minimumHeight,maximumHeight)
        velocityHeight=if(height==h) baseHeight.velocity+extraHeight.velocity else 0f
        y=extraY.sample(extraTime); velocityY=extraY.velocity
    }
    fun settled(time: Float)=time>=.20f && abs(width-targetWidth)<.25f && abs(height-targetHeight)<.25f &&
        abs(y)<.25f && abs(velocityWidth)<2f && abs(velocityHeight)<2f && abs(velocityY)<2f
}
