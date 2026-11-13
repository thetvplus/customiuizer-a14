package tv.withaibuild.customiuizer.mods.recents

import android.animation.Animator
import android.view.MotionEvent
import android.view.View
import android.animation.ObjectAnimator
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Field
import java.lang.reflect.Method

/** Cold exact-contract resolution. Nothing is hooked until the complete table
 * has resolved; all rendering/input calls reuse these members. */
internal enum class DeckLauncherProfile {
    LEGACY, WINDOW_ELEMENT;
    companion object {
        fun resolve(loader: ClassLoader) = if (XposedHelpers.findClassIfExists(
            "com.miui.home.recents.anim.WindowElement", loader) != null) WINDOW_ELEMENT else LEGACY
    }
}

internal class DeckNativeApi(private val loader: ClassLoader,val profile: DeckLauncherProfile=DeckLauncherProfile.LEGACY) {
    private fun cls(name: String)=XposedHelpers.findClass("com.miui.home.$name",loader)
    private fun field(type: Class<*>,name: String,expected: Class<*>,static: Boolean=false): Field = XposedHelpers.findField(type,name).apply {
        require(this.type==expected && java.lang.reflect.Modifier.isStatic(modifiers)==static); isAccessible=true
    }
    fun exact(type: Class<*>,name: String,result: Class<*>,vararg parameters: Class<*>): Method =
        type.getDeclaredMethod(name,*parameters).apply { require(returnType==result && !java.lang.reflect.Modifier.isStatic(modifiers)); isAccessible=true }
    fun staticExact(type: Class<*>,name: String,result: Class<*>,vararg parameters: Class<*>): Method =
        type.getDeclaredMethod(name,*parameters).apply { require(returnType==result && java.lang.reflect.Modifier.isStatic(modifiers)); isAccessible=true }
    private val floatType=Float::class.javaPrimitiveType!!
    private val voidType=Void.TYPE
    val recentsContainer=cls("recents.views.RecentsContainer")
    val recentsRotation=field(recentsContainer,"mRecentsContainerRotation",Int::class.javaPrimitiveType!!)
    val updateRotation=exact(recentsContainer,"updateRotation",voidType,Int::class.javaPrimitiveType!!)
    val stack=cls("recents.views.TaskStackView")
    val algorithm=cls("recents.views.TaskStackLayoutAlgorithm")
    val horizontal=cls("recents.views.TaskStackViewsAlgorithmHorizontal")
    val transform=cls("recents.views.TaskViewTransform")
    val taskView=cls("recents.views.TaskView")
    val fullscreenProgress=field(taskView,"mFullscreenProgress",floatType)
    private val header=cls("recents.views.TaskViewHeader")
    val headerIcon=field(header,"mIconView",android.widget.ImageView::class.java)
    val headerTitle=field(header,"mTitleView",android.widget.TextView::class.java)
    val headerBadge=field(header,"mLockedImageView",android.widget.ImageView::class.java)
    val handler=cls("recents.views.TaskStackViewTouchHandler")
    val recents=cls("recents.views.RecentsView")
    val nav=cls("recents.NavStubView")
    val quick=cls("recents.QuickstepAppTransitionManagerImpl")
    val utils=cls("recents.TaskViewUtils")
    val spring=cls("recents.util.RectFSpringAnim")
    val props=XposedHelpers.findClass("com.android.systemui.shared.recents.utilities.AnimationProps",loader)
    val current=field(stack,"mLayoutAlgorithm",algorithm)
    val stable=field(stack,"mStableLayoutAlgorithm",algorithm)
    val touch=field(stack,"mTouchHandler",handler)
    val scroller=field(stack,"mStackScroller",cls("recents.views.TaskStackViewScroller"))
    private val horizontalScroller=cls("recents.views.TaskStackViewHorizontalScroller")
    val fling=exact(horizontalScroller,"fling",voidType,floatType,floatType,floatType,Int::class.javaPrimitiveType!!,Int::class.javaPrimitiveType!!,Int::class.javaPrimitiveType!!,Int::class.javaPrimitiveType!!,Int::class.javaPrimitiveType!!,Int::class.javaPrimitiveType!!,Int::class.javaPrimitiveType!!,Int::class.javaPrimitiveType!!,Int::class.javaPrimitiveType!!)
    val nearest=exact(horizontalScroller,"getNearestMotionlessScrollP",floatType,floatType)
    val scroll=field(scroller.type,"mStackScrollP",floatType)
    val base=field(algorithm,"mTaskViewRectF",android.graphics.RectF::class.java)
    val window=field(algorithm,"mWindowRect",android.graphics.Rect::class.java)
    val visualInsets=field(algorithm,"mVisualRotationSystemInsets",android.graphics.Rect::class.java).apply { require(type==android.graphics.Rect::class.java) }
    val visualLandscape=exact(algorithm,"isLandscapeVisually",Boolean::class.javaPrimitiveType!!)
    val padding=field(algorithm,"mTaskStackViewPaddingRect",android.graphics.Rect::class.java)
    val count=field(algorithm,"mNumStackTasks",Int::class.javaPrimitiveType!!)
    val gap=field(algorithm,"mHorizontalGap",floatType)
    val minimum=field(algorithm,"mMinScrollP",floatType)
    val headerHeight=field(algorithm,"mRecentsTaskViewHeaderHeight",Int::class.javaPrimitiveType!!)
    val rect=field(transform,"rect",android.graphics.RectF::class.java)
    val scale=field(transform,"scale",floatType)
    val z=field(transform,"translationZ",floatType)
    val visible=field(transform,"visible",Boolean::class.javaPrimitiveType!!)
    val overview=field(recents,"mInOverviewState",Boolean::class.javaPrimitiveType!!)
    val recentsStack=field(recents,"mTaskStackView",stack)
    val split=field(recents,"mIsInSplitSelectState",Boolean::class.javaPrimitiveType!!)
    val getTask=exact(taskView,"getTask",XposedHelpers.findClass("com.android.systemui.shared.recents.model.Task",loader))
    val task=getTask.returnType
    val key=field(task,"key",XposedHelpers.findClass("com.android.systemui.shared.recents.model.Task\$TaskKey",loader))
    val id=field(key.type,"id",Int::class.javaPrimitiveType!!)
    val windowMode=field(key.type,"windowingMode",Int::class.javaPrimitiveType!!)
    val multiple=exact(task,"hasMultipleTasks",Boolean::class.javaPrimitiveType!!)
    val getStack=exact(stack,"getStack",XposedHelpers.findClass("com.android.systemui.shared.recents.model.TaskStack",loader))
    val getTasks=exact(getStack.returnType,"getStackTasks",java.util.ArrayList::class.java)
    val ignored=exact(stack,"isIgnoredTask",Boolean::class.javaPrimitiveType!!,task)
    val getHeader=exact(taskView,"getHeaderView",header)
    val getThumbnail=exact(taskView,"getThumbnailView",cls("recents.views.TaskViewThumbnail"))
    // Both Launcher families draw with this native thumbnail radius.
    val thumbnailRadius=field(getThumbnail.returnType,"mCornerRadius",Int::class.javaPrimitiveType!!)
    val headerTransform=exact(taskView,"setHeaderTranslationAndAlpha",voidType,floatType,floatType,floatType)
    val childrenAlpha=exact(taskView,"setChildrenViewAlpha",voidType,floatType)
    val layout=exact(stack,"updateLayoutStyle",voidType)
    val rebaseScroll=exact(stack,"changeTaskStackViewScrollAndTaskViewTranslation",voidType,floatType)
    val cardSprings=DeckCardSpring(taskView)
    val attached=exact(stack,"onAttachedToWindow",voidType)
    val detached=exact(stack,"onDetachedFromWindow",voidType)
    val configuration=exact(stack,"onConfigurationChanged",voidType,android.content.res.Configuration::class.java)
    val update=exact(algorithm,"update",voidType,XposedHelpers.findClass("com.android.systemui.shared.recents.model.TaskStack",loader),android.util.ArraySet::class.java)
    val bounds=exact(horizontal,"getTaskViewTransform",voidType,Int::class.javaPrimitiveType!!,floatType,transform)
    val minimumScroll=exact(horizontal,"calculateMinScrollP",floatType)
    val content=exact(algorithm,"getTaskViewWithoutHeaderRectF",android.graphics.RectF::class.java,Int::class.javaPrimitiveType!!,floatType)
    val taskTransform=exact(algorithm,"getTaskViewTransform",transform,task,floatType,transform)
    val apply=exact(transform,"applyToTaskView",voidType,taskView,java.util.ArrayList::class.java,props,Boolean::class.javaPrimitiveType!!)
    val copy=exact(transform,"copyFrom",voidType,transform)
    val fill=exact(transform,"fillIn",voidType,taskView)
    val reset=exact(transform,"reset",voidType)
    val poolReturn=exact(stack,"onReturnViewToPool",voidType,taskView)
    val poolPickup=exact(stack,"onPickUpViewFromPool",voidType,taskView,task,Boolean::class.javaPrimitiveType!!)
    val intercept=exact(stack,"onInterceptTouchEvent",Boolean::class.javaPrimitiveType!!,MotionEvent::class.java)
    val onTouch=exact(stack,"onTouchEvent",Boolean::class.javaPrimitiveType!!,MotionEvent::class.java)
    val nativeIntercept=exact(handler,"onInterceptTouchEvent",Boolean::class.javaPrimitiveType!!,MotionEvent::class.java)
    val find=exact(handler,"findViewAtPoint",taskView,floatType,floatType)
    val hit=exact(stack,"isTouchPointInView",Boolean::class.javaPrimitiveType!!,floatType,floatType,taskView)
    val interpolate=exact(handler,"updateTaskViewTransforms",voidType,floatType)
    val dragEnd=exact(handler,"onDragEnd",voidType,android.view.View::class.java)
    val childSnapped=exact(handler,"onChildSnappedBack",voidType,View::class.java,floatType)
    private val swipe=cls("recents.views.SwipeHelperForRecents")
    val swipeDirection=field(swipe,"mSwipeDirection",Int::class.javaPrimitiveType!!)
    val dismissAnimator=exact(swipe,"getViewTranslationAnimator",ObjectAnimator::class.java,View::class.java,floatType)
    private val vertical=cls("recents.views.VerticalSwipe")
    val verticalSwipe=field(swipe,"mVerticalSwipe",vertical).apply { require(type==vertical) }
    val swipeTouch=exact(swipe,"onTouchEvent",Boolean::class.javaPrimitiveType!!,MotionEvent::class.java)
    val snapChild=exact(swipe,"snapChild",voidType,View::class.java,floatType)
    val verticalCalculate=exact(vertical,"calculate",voidType,floatType)
    // The verified native families override these callbacks without calling super.
    private val swipeCallbacks=cls("recents.views.TaskStackViewTouchHandler\$1")
    val swipeMove=exact(swipeCallbacks,"onMoveUpdate",voidType,View::class.java,floatType,floatType)
    val prepareSnap=exact(swipeCallbacks,"prepareSnapBackAnimation",voidType,View::class.java,Any::class.java)
    val currentTasks=field(handler,"mCurrentTasks",java.util.ArrayList::class.java)
    val startTransforms=field(handler,"mCurrentTaskTransforms",java.util.ArrayList::class.java)
    val endTransforms=field(handler,"mFinalTaskTransforms",java.util.ArrayList::class.java)
    val updateView=exact(stack,"updateTaskViewToTransform",voidType,taskView,transform,props)
    val immediate=field(props,"IMMEDIATE",props,true).get(null)
    val isImmediate=exact(props,"isImmediate",Boolean::class.javaPrimitiveType!!)
    val applyTiming=exact(props,"apply",android.animation.ValueAnimator::class.java,Int::class.javaPrimitiveType!!,android.animation.ValueAnimator::class.java)
    val navVisible=exact(nav,"modifyTransformVisible",voidType,java.util.ArrayList::class.java)
    val navToRecents=exact(nav,"performAppToRecents",voidType,Boolean::class.javaPrimitiveType!!)
    val gestureMode=exact(nav,"getCurrentWindowMode",Int::class.javaPrimitiveType!!,MotionEvent::class.java,
        Boolean::class.javaPrimitiveType!!,Boolean::class.javaPrimitiveType!!,Boolean::class.javaPrimitiveType!!)
    val quickSwitch=exact(nav,"performAppToApp",voidType,Int::class.javaPrimitiveType!!)
    val halfQuickSwitch=exact(nav,"performHalfAppToHalfApp",voidType,Int::class.javaPrimitiveType!!)
    val navSpring=if(profile==DeckLauncherProfile.LEGACY) field(nav,"mAppToRecentsAnim2",spring) else null
    val closing=if(profile==DeckLauncherProfile.LEGACY) exact(quick,"startClosingWindowToRecentsAnimators",spring,Class.forName("[Lcom.android.systemui.shared.recents.system.RemoteAnimationTargetCompat;",false,loader),Class.forName("[Lcom.android.systemui.shared.recents.system.RemoteAnimationTargetCompat;",false,loader),cls("recents.util.MultiAnimationEndDetector")) else null
    val windowElement=if(profile==DeckLauncherProfile.WINDOW_ELEMENT) DeckWindowApi(loader) else null
    val gesturePose=if(profile==DeckLauncherProfile.WINDOW_ELEMENT) exact(cls("recents.anim.TaskViewsElement"),"updateTaskView",voidType,
        taskView,Int::class.javaPrimitiveType!!,android.graphics.RectF::class.java,
        Int::class.javaPrimitiveType!!,Int::class.javaPrimitiveType!!,floatType,floatType,Boolean::class.javaPrimitiveType!!,
        floatType,floatType,Runnable::class.java,floatType,floatType,Boolean::class.javaPrimitiveType!!,
        Int::class.javaPrimitiveType!!,floatType,Int::class.javaPrimitiveType!!,android.graphics.Rect::class.java) else
        exact(nav,"updateTaskView",voidType,taskView,Int::class.javaPrimitiveType!!,android.graphics.RectF::class.java,
            Int::class.javaPrimitiveType!!,Int::class.javaPrimitiveType!!,floatType,floatType,Boolean::class.javaPrimitiveType!!,
            floatType,floatType,Runnable::class.java)
    val gestureProgress=if(profile==DeckLauncherProfile.LEGACY) field(nav,"mPer",floatType) else null
    val gestureQuickSwitch=if(profile==DeckLauncherProfile.LEGACY) exact(nav,"isQuickSwitchMode",Boolean::class.javaPrimitiveType!!) else null
    val navWindowMode=if(profile==DeckLauncherProfile.LEGACY) field(nav,"mWindowMode",Int::class.javaPrimitiveType!!) else null
    val surfaceOcclusion=if(profile==DeckLauncherProfile.WINDOW_ELEMENT) DeckSurfaceOcclusion(cls("recents.util.ClipAnimationHelper"))
        else DeckSurfaceOcclusion.resolve(cls("recents.util.ClipAnimationHelper"))
    val springStart=if(profile==DeckLauncherProfile.LEGACY) exact(spring,"getStartRect",android.graphics.RectF::class.java) else null
    val legacySpringStarts=if(profile==DeckLauncherProfile.LEGACY) listOf(
        exact(spring,"startInGestureThread",voidType),exact(spring,"startInMainThread",voidType)) else emptyList()
    val legacySurfaceFrame=if(profile==DeckLauncherProfile.LEGACY && surfaceOcclusion!=null) staticExact(utils,
        "lambda\$createSpringAnim\$1",voidType,cls("recents.util.ClipAnimationHelper\$TransformParams"),
        cls("recents.util.ClipAnimationHelper"),cls("recents.util.RemoteAnimationTargetSet"),
        Boolean::class.javaPrimitiveType!!,Boolean::class.javaPrimitiveType!!,Int::class.javaPrimitiveType!!,
        Boolean::class.javaPrimitiveType!!,taskView,Boolean::class.javaPrimitiveType!!,recents,android.graphics.RectF::class.java,
        Boolean::class.javaPrimitiveType!!,android.graphics.RectF::class.java,floatType,floatType,floatType) else null
    val createSpring=staticExact(utils,"createSpringAnim",spring,recents,taskView,cls("recents.util.ClipAnimationHelper\$TransformParams"),cls("recents.util.ClipAnimationHelper"),cls("recents.util.RemoteAnimationTargetSet"),android.graphics.RectF::class.java,android.graphics.RectF::class.java,floatType,floatType,Boolean::class.javaPrimitiveType!!,Int::class.javaPrimitiveType!!)
    val listen=exact(spring,"addAnimatorListener",voidType,Animator.AnimatorListener::class.java)
    val unlisten=exact(spring,"removeAnimatorListener",voidType,Animator.AnimatorListener::class.java)
    val running=exact(spring,"isRunning",Boolean::class.javaPrimitiveType!!)
    val springReset=if(profile==DeckLauncherProfile.LEGACY) exact(spring,"reset",voidType,
        android.graphics.RectF::class.java,android.graphics.RectF::class.java,
        floatType,floatType,floatType,floatType) else null
    val click=exact(taskView,"onClick",voidType,android.view.View::class.java)
    // Both admitted Legacy families gate neighbor alignment on clip mode 1.
    // Keep its native clip mode; align the UI and crop the opening leash through
    // their respective existing callbacks instead of changing that mode globally.
    val legacyOpeningFrame=if(profile==DeckLauncherProfile.LEGACY) staticExact(utils,
        "lambda\$createSpringAnim\$0",voidType,taskView,floatType,Boolean::class.javaPrimitiveType!!,
        Int::class.javaPrimitiveType!!,recents,android.graphics.RectF::class.java,android.graphics.RectF::class.java) else null
    val alignOpen=exact(recents,"alignTaskViewWhenTaskLaunch",voidType,taskView,android.graphics.RectF::class.java,android.graphics.RectF::class.java,floatType,floatType)
    val alignClose=exact(recents,"alignTaskViewWhenAppToRecent",voidType,taskView,android.graphics.RectF::class.java,android.graphics.RectF::class.java,floatType,floatType)
    val setOverview=exact(recents,"setInOverviewState",voidType,Boolean::class.javaPrimitiveType!!)
    val splitStart=exact(recents,"initiateSplitSelect",voidType,task,Int::class.javaPrimitiveType!!)
    val splitEnd=exact(recents,"resetInitiatedSelect",voidType)
    init {
        require(swipeCallbacks.superclass==swipe)
        require(android.view.ViewGroup::class.java.isAssignableFrom(stack) && View::class.java.isAssignableFrom(taskView))
    }
}
