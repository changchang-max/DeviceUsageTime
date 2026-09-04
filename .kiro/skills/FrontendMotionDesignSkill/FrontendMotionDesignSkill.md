# Frontend Motion Design Skill

## 目标

设计并实现具有高级视觉表现力的现代 Web 前端页面。

核心原则：

> 简约的视觉结构 + 丰富的动态效果 + 高质量交互 + JavaScript 驱动的视觉反馈。

页面应该第一眼保持克制、干净、有秩序，但用户移动鼠标、滚动页面、点击元素、切换状态时，能够持续获得细腻而有层次的动态反馈。

不要为了“特效多”而堆砌动画。每一个效果都应该服务于层次、空间感、反馈或品牌表达。

---

## 1. Visual Direction

默认采用现代、极简、高级的视觉语言：

- 大面积留白
- 强烈的排版层级
- 少量核心色彩
- 高质量字体
- 大尺寸标题
- 精细边框
- 半透明材质
- Blur
- Glow
- Gradient
- Noise
- Grid
- 深浅层次
- 微妙阴影

避免传统的：

- 大量卡片堆叠
- 廉价渐变
- 过度圆角
- 厚重阴影
- 无意义装饰
- 每个元素都在不停运动

页面结构应该简单，但细节应该丰富。

---

## 2. JavaScript First

交互效果优先使用 JavaScript 驱动。

不要只依赖：

```css
:hover
```

应该根据场景使用：

- pointermove
- mousemove
- pointerenter
- pointerleave
- scroll
- resize
- IntersectionObserver
- requestAnimationFrame
- keyboard events
- touch events

通过 JS 控制：

- 鼠标跟随
- 元素位移
- 旋转
- 缩放
- 光照
- Glow
- Parallax
- Scroll progress
- Reveal animation
- Magnetic interaction
- Cursor interaction
- 3D tilt
- 页面过渡
- 动态背景
- 粒子
- Canvas
- SVG animation

---

## 3. Cursor Interaction

桌面端页面应该考虑自定义 Cursor。

可以实现：

- Dot cursor
- Ring cursor
- Cursor trailing
- Hover expansion
- Cursor blend mode
- Magnetic buttons
- Cursor text
- Cursor state

例如：

普通状态：

```text
●
```

Hover Button：

```text
◎
```

Hover Image：

```text
VIEW
```

Cursor 动画应该平滑，使用 interpolation 或 requestAnimationFrame。

不要让 Cursor 影响正常点击和可访问性。

---

## 4. Magnetic Interaction

重要 CTA 可以使用磁吸效果。

当鼠标靠近按钮时：

```text
cursor → button
```

按钮产生轻微位移。

推荐：

- 最大位移：8~20px
- 使用 lerp 平滑跟随
- mouseleave 后自然回弹
- 不要产生明显抖动

磁吸效果主要用于：

- CTA
- Navigation
- Icon Button
- Social links

不要所有按钮都磁吸。

---

## 5. Mouse Reactive Elements

允许页面根据鼠标位置产生视觉变化。

例如：

```text
mouse position
      ↓
    JS
      ↓
CSS variables
      ↓
transform / glow / gradient / blur
```

可以实现：

- Radial spotlight
- Gradient following cursor
- Background glow
- Card tilt
- Image parallax
- Border illumination
- Text displacement

例如动态背景：

```css
background:
radial-gradient(
  circle at var(--mouse-x) var(--mouse-y),
  rgba(...),
  transparent 30%
);
```

鼠标移动时通过 JavaScript 更新 CSS variables。

---

## 6. 3D Tilt

Card、Image、Product Preview 可以响应鼠标产生轻微 3D 倾斜。

推荐：

```text
rotateX()
rotateY()
perspective()
```

效果应该非常克制。

例如：

```text
mouse left
    ↓
card slightly rotates right
```

同时可以改变：

- shadow
- highlight
- reflection
- glow

让元素产生真实的空间感。

---

## 7. Scroll Animation

页面滚动应该是重要的交互来源。

使用：

```text
IntersectionObserver
requestAnimationFrame
scroll progress
```

实现：

- Fade in
- Slide up
- Blur → sharp
- Scale
- Parallax
- Clip-path reveal
- Text reveal
- Image reveal
- Horizontal scrolling
- Sticky sections
- Scroll-linked animation

推荐动画节奏：

```text
opacity: 0 → 1
transform: translateY(30px) → 0
filter: blur(10px) → blur(0)
```

不要让整个页面所有元素同时飞进来。

---

## 8. Text Animation

标题应该拥有动态表现。

可以实现：

### Character Reveal

```text
H E L L O
↓
逐字出现
```

### Word Reveal

```text
Build
better
interfaces
```

逐行进入。

### Mask Reveal

文字从裁切区域中出现。

### Scramble Text

例如：

```text
LOADING
↓
L8A2I9N
↓
LOADING
```

适用于：

- Hero
- Navigation
- Section heading
- Loading
- Status

不要在普通正文上使用。

---

## 9. Image Interaction

图片不应该只是静态 `<img>`。

可以添加：

- Hover zoom
- Parallax
- Mask reveal
- Clip-path animation
- Grayscale → color
- Blur → sharp
- Mouse tracking
- Image distortion
- WebGL shader
- 3D perspective

例如：

```text
Image
   ↓
Mouse movement
   ↓
Subtle parallax
   ↓
Light reflection
```

保持内容可读。

---

## 10. Dynamic Background

背景可以使用 JavaScript 创造空间感。

可使用：

- Canvas
- SVG
- CSS gradients
- Noise texture
- Particles
- Grid
- Floating objects
- Aurora
- Mesh gradient
- WebGL

优先考虑：

```text
Canvas + requestAnimationFrame
```

而不是大量 DOM 元素。

背景动画应该低频、缓慢、连续。

不要让背景抢走内容。

---

## 11. Canvas Effects

适合使用 Canvas 实现：

- Particle field
- Star field
- Noise
- Fluid-like movement
- Mouse trails
- Connection lines
- Interactive dots
- Wave
- Gradient animation

基本结构：

```js
const canvas = document.querySelector('canvas')
const ctx = canvas.getContext('2d')

function animate() {
  update()
  render()
  requestAnimationFrame(animate)
}

animate()
```

必须处理：

- devicePixelRatio
- resize
- cleanup
- performance
- reduced motion

避免创建数千个 DOM 节点模拟粒子。

---

## 12. Page Transitions

页面之间应该有连续性。

可以使用：

- Fade
- Clip-path
- Scale
- Slide
- Shared element
- View Transition API

推荐：

```text
old page
   ↓
transition
   ↓
new page
```

避免传统的瞬间跳转。

如果项目环境支持 View Transition API，应优先考虑。

---

## 13. Button Interaction

按钮需要完整的交互状态：

```text
default
hover
active
focus
loading
success
error
disabled
```

Hover 可以产生：

- background transition
- border glow
- text movement
- icon movement
- magnetic effect
- shine
- arrow translation

例如：

```text
Explore →
```

Hover：

```text
Explore   →
             ↗
```

动画应该在 150~400ms 范围内完成。

---

## 14. Navigation

导航保持极简。

可以使用：

- Transparent header
- Sticky header
- Blur background
- Scroll-aware header
- Hide/show on scroll
- Active indicator
- Magnetic nav items
- Fullscreen menu

滚动时：

```text
large header
      ↓
compact header
```

通过 JavaScript 判断 scroll direction。

---

## 15. Loading Experience

不要简单使用：

```text
Loading...
```

可以使用：

- Progress bar
- Number counter
- Logo reveal
- Blur reveal
- SVG animation
- Canvas loader

例如：

```text
00
17
42
68
91
100
```

加载完成后进行一次完整的页面 reveal。

但如果页面实际加载很快，不要人为制造 3 秒 Loading。让用户等待只是为了展示你的动画，属于非常典型的人类自我感动。

---

## 16. Micro Interaction

所有交互元素都应该拥有细微反馈。

例如：

Checkbox：

```text
□
↓
✓
```

Toggle：

```text
OFF → ON
```

Input：

```text
focus
↓
border glow
↓
label movement
```

Dropdown：

```text
scale + opacity + translateY
```

Tooltip：

```text
opacity + translateY
```

这些小动画比大型炫技更能体现完成度。

---

## 17. Motion System

建立统一 Motion System。

推荐：

```js
duration:
fast   = 150ms
normal = 300ms
slow   = 600ms
dramatic = 1000ms+
```

Easing 优先：

```text
cubic-bezier
ease-out
ease-in-out
spring-like easing
```

动画应该有层级：

```text
Micro interaction
      ↓
Component animation
      ↓
Section animation
      ↓
Page transition
```

不要让所有层级同时使用最大动画强度。

---

## 18. Performance

特效多不等于性能可以随便浪费。

必须：

- 优先 transform / opacity
- 避免频繁触发 layout
- requestAnimationFrame
- throttle / debounce
- IntersectionObserver
- GPU-friendly animation
- Lazy loading
- Canvas 生命周期管理
- 清理 event listeners
- 避免大量 DOM animation

避免：

```js
mousemove → setState()
```

每一帧触发 React 全树重新渲染。

优先：

```text
pointermove
    ↓
requestAnimationFrame
    ↓
CSS variables / transform
```

---

## 19. Accessibility

所有特效必须服从可用性。

支持：

```css
@media (prefers-reduced-motion: reduce) {
  /* minimize motion */
}
```

同时保证：

- 键盘导航
- Focus 状态
- 足够对比度
- Screen reader 可用
- Cursor 特效不能阻止点击
- 动画不能成为获取信息的唯一方式

---

## 20. Responsive Behavior

移动端不能简单把桌面动画缩小。

移动端应该主动降低：

- Cursor effects
- 3D tilt
- Particle count
- Parallax intensity
- Blur
- Background complexity

触摸设备可以替换成：

```text
tap
swipe
scroll
press
```

而不是模拟鼠标。

---

## 21. Technology Preference

优先考虑：

- React
- Next.js
- TypeScript
- Tailwind CSS
- CSS Variables
- Framer Motion / Motion
- GSAP
- Lenis
- Three.js
- React Three Fiber
- Canvas
- SVG
- View Transition API

技术选择应该根据效果需求决定。

简单 hover 不需要 GSAP。

复杂时间轴动画可以使用 GSAP。

3D / WebGL 才使用 Three.js。

---

## 22. Design Rule

页面最终应该符合：

```text
Simple structure
        +
Strong typography
        +
Minimal palette
        +
Rich interaction
        +
Subtle motion
        +
Excellent micro-interactions
```

而不是：

```text
gradient
+ glow
+ particles
+ blur
+ 3D
+ animation
+ animation
+ animation
= design
```

特效应该让用户觉得：

> “这个页面很有质感。”

而不是：

> “这个页面是不是显卡欠它钱？”