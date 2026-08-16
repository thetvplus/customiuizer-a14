.class public Lcom/example/Sample;
.super Ljava/lang/Object;

.method public constructor <init>()V
    .registers 1
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public foo()V
    .registers 1
    return-void
.end method

.method public foo(I)V
    .registers 2
    return-void
.end method

.method public bar(Ljava/lang/String;)Z
    .registers 3
    const/4 v0, 0x0
    return v0
.end method

.method private qux()V
    .registers 1
    return-void
.end method

.field public mValue:I

.field private mHidden:Ljava/lang/String;
