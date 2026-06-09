# 修复GameRoom出牌并发问题

> **Workspace:** /home/jiangyong/ws/bytedance/solo0601/JY-062/JY-062-18

---

**User**

GameRoom.handlePlay()中playerHasCards()与出牌逻辑非原子操作，高并发下两个玩家同时出牌可导致同一张牌被多次打出，需引入ReentrantLock粒化到玩家级别，确保出牌校验与手牌扣除的原子性。

**Assistant**

### Relevant Code Snippets
1. /home/jiangyong/ws/bytedance/solo0601/JY-062/JY-062-18/src/main/java/com/ddz/server/GameRoom.java:L1-L100
  — 找到GameRoom类文件，该文件可能包含handlePlay和playerHasCards相关逻辑，需要进一步分析其内容确认并发问题。
2. /home/jiangyong/ws/bytedance/solo0601/JY-062/JY-062-18/src/main/java/com/ddz/server/GameRoom.java:L100-L200
  — 继续读取GameRoom类的后续部分，查找handlePlay方法及playerHasCards相关实现。



让我检查一下 GameRoom 类中的 handlePlay 和 playerHasCards 方法，以及可能存在的并发问题。



### Relevant Code Snippets
1. /home/jiangyong/ws/bytedance/solo0601/JY-062/JY-062-18/src/main/java/com/ddz/server/GameRoom.java:L200-L265
  — handlePlay方法是处理玩家出牌的核心逻辑，它被声明为synchronized，但存在潜在的并发问题。在方法内部，它调用了playerHasCards方法来验证玩家是否拥有出牌，然而在处理出牌过程中，对玩家手牌的操作（如remove）没有完全同步保护。

2. /home/jiangyong/ws/bytedance/solo0601/JY-062/JY-062-18/src/main/java/com/ddz/server/GameRoom.java:L273-L292
  — playerHasCards方法用于验证玩家是否有足够的牌出，虽然该方法是私有的且不直接被并发调用，但它被handlePlay方法调用时，在多线程环境下可能产生竞态条件。

3. /home/jiangyong/ws/bytedance/solo0601/JY-062/JY-062-18/src/main/java/com/ddz/server/GameRoom.java:L240-L244
  — 在handlePlay方法中，直接修改了玩家的手牌列表，该操作没有额外的同步保护，在高并发场景下可能导致手牌数据不一致。

4. /home/jiangyong/ws/bytedance/solo0601/JY-062/JY-062-18/src/main/java/com/ddz/server/GameRoom.java:L246-L247
  — 修改lastPlayedCards和lastPlayType等共享状态变量时，虽然整个handlePlay方法是synchronized的，但变量更新顺序可能导致其他线程观察到不一致的状态。

现在我对问题有了清晰的理解。让我来分析并实现修复。

**问题分析：**
当前 [handlePlay](file:///home/jiangyong/ws/bytedance/solo0601/JY-062/JY-062-18/src/main/java/com/ddz/server/GameRoom.java#L200-L265) 使用 `synchronized` 方法级锁，这是粗粒度锁。`playerHasCards()` 校验（第211行）与手牌扣除（第242-244行）之间虽然被 `synchronized` 包裹，但方法级锁意味着：
1. 同一玩家的并发出牌请求在锁释放后仍可能让第二请求通过了 `playerHasCards` 校验（因为第一请求虽已扣牌但 `synchronized` 已释放），但实际上 `synchronized` 方法级锁确保了串行执行
2. 更重要的是，`synchronized` 是粗粒度的——所有玩家操作被整体互斥，无法做到玩家级别的出牌原子性保障

**修复方案：** 引入 `ReentrantLock` 粒化到玩家级别，移除方法级 `synchronized`，使用玩家锁确保 `playerHasCards` 校验与手牌扣除的原子性，同时用 `synchronized(this)` 保护共享游戏状态。

现在我来着手实现这些修改：

现在，当玩家被添加时，添加锁的初始化：

在 `removePlayer()` 中添加锁清理：

接下来是关键的改动——重构 `handlePlay()` 以使用每个玩家的 `ReentrantLock`，而不是方法级的 `synchronized`：

让我检查一下构建配置并编译：

编译成功，无错误。让我再做一次最终检查，确认修改后的完整文件逻辑正确：

编译和诊断均通过，修复完成。以下是修改总结：

---

## 修改内容

### 问题根因

[handlePlay](file:///home/jiangyong/ws/bytedance/solo0601/JY-062/JY-062-18/src/main/java/com/ddz/server/GameRoom.java#L203-L279) 原先使用 `synchronized` 方法级锁，`playerHasCards()` 校验（第220行）与手牌扣除 `hand.remove()`（第251-253行）虽然被包裹在同一个 `synchronized` 块内，但：

1. **方法级锁粒度过粗** — 所有玩家的出牌、叫地主、PASS 操作共享同一把 `this` 监视器锁，无法做到玩家级别的并发隔离
2. **校验与扣除无显式原子保障** — 若未来移除 `synchronized`，同一玩家的并发出牌请求可穿透 `playerHasCards()` 校验（第一次请求已通过校验但尚未扣牌，第二次请求同样通过校验），导致同一张牌被重复打出

### 修复方案：引入玩家级 ReentrantLock

**核心改动（4处）：**

1. **新增字段** — [playerLocks](file:///home/jiangyong/ws/bytedance/solo0601/JY-062/JY-062-18/src/main/java/com/ddz/server/GameRoom.java#L20)：`ConcurrentHashMap<String, ReentrantLock>`，为每个玩家分配独立的锁实例

2. **生命周期管理**：
   - [addPlayer()](file:///home/jiangyong/ws/bytedance/solo0601/JY-062/JY-062-18/src/main/java/com/ddz/server/GameRoom.java#L53)：玩家加入时 `playerLocks.put(playerId, new ReentrantLock())`
   - [removePlayer()](file:///home/jiangyong/ws/bytedance/solo0601/JY-062/JY-062-18/src/main/java/com/ddz/server/GameRoom.java#L401)：玩家离开时 `playerLocks.remove(playerId)`

3. **handlePlay() 重构** — 移除 `synchronized` 方法修饰符，采用双层锁策略：
   - **外层**：`playerLock.lock()/unlock()` — 确保同一玩家的 `playerHasCards()` 校验与 `hand.remove()` 扣除在同一把锁下原子完成
   - **内层**：`synchronized(this)` — 保护共享游戏状态（`currentPlayerIndex`、`lastPlayedCards`、`passCount` 等）

4. **锁顺序保证** — 始终先获取 playerLock 再进入 `synchronized(this)`，避免死锁

### 并发安全保证

| 场景 | 修复前 | 修复后 |
|------|--------|--------|
| 同一玩家并发出牌 | 依赖方法级 synchronized，隐式原子 | **playerLock 显式保证**校验+扣除原子性 |
| 不同玩家同时操作 | 全局互斥，吞吐低 | playerLock 隔离，不同玩家可并行进入校验阶段 |
| 共享游戏状态 | synchronized 保护 | synchronized(this) 精确保护 |

---

