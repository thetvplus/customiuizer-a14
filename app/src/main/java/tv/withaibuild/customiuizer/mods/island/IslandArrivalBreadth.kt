package tv.withaibuild.customiuizer.mods.island

/** One physical width, retargeted on the caller's finite clock. The selected
 * 22G100 Home profiles/timers guide this A14 arrival mapping; applying it to
 * ordinary arrivals is a product adaptation, not a recovered iOS routing fact. */
internal class IslandArrivalBreadth(from: Float,private val target: Float,velocity: Float,density: Float,
    private val minimum: Float,private val maximum: Float) {
    private companion object {
        val stretch=IslandMotion.Profile(.5,.714)
        val rebound=IslandMotion.Profile(.35,1.0)
        val accepted=IslandMotion.Profile(.513,.525)
    }
    private val excursion=16f*density
    private val spring=IslandMotion.Spring().apply {
        setVelocity(velocity);retarget(from,target+excursion,stretch,true)
    }
    val durationMillis=200L+accepted.horizon(kotlin.math.abs(from-target)+excursion,
        kotlin.math.abs(velocity)+((kotlin.math.abs(from-target)+excursion)*stretch.omega).toFloat())
    private var stage=1
    var width=from; private set
    var velocity=velocity; private set
    fun sample(time: Float) {
        if(stage==1 && time>=.12f) {
            spring.retarget(spring.sample(.12f,minimum,maximum),target,rebound,true);stage=2
        }
        if(stage==2 && time>=.20f) {
            spring.retarget(spring.sample(.08f,minimum,maximum),target,accepted,true);stage=3
        }
        val elapsed=if(stage==3) time-.20f else if(stage==2) time-.12f else time
        width=spring.sample(elapsed.coerceAtLeast(0f),minimum,maximum)
        velocity=spring.velocity
    }
}
