"use client";

// Decorative only. The 3D renders are original, transparent, optimized WebP stickers.
const sprites = [
  { kind:"smile", top:"2%", left:"3%", size:92, rotation:-13 },
  { kind:"party", top:"9%", left:"68%", size:108, rotation:18 },
  { kind:"soju", top:"24%", left:"1%", size:98, rotation:-15 },
  { kind:"star", top:"35%", left:"73%", size:100, rotation:14 },
  { kind:"balloon", top:"53%", left:"6%", size:88, rotation:8 },
  { kind:"confetti", top:"65%", left:"70%", size:100, rotation:-9 },
  { kind:"smile", top:"78%", left:"43%", size:90, rotation:12 },
  { kind:"soju", top:"92%", left:"77%", size:93, rotation:15 },
] as const;

export function MtPartyBackdrop() {
  return <div className="mt-party-backdrop" aria-hidden="true">
    {sprites.map((sprite,i)=><span
      key={i}
      className={`mt-party-sprite mt-party-sprite-${sprite.kind}`}
      style={{
        top:sprite.top,
        left:sprite.left,
        width:sprite.size,
        height:sprite.size,
        transform:`rotate(${sprite.rotation}deg)`,
      }}
    />)}
  </div>;
}
