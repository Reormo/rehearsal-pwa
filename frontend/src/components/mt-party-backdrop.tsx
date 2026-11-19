"use client";

// Decorative only. Resolution-independent glossy 3D-style SVG stickers.
const sprites = [
  {kind:"smile",top:"2%",left:"3%",size:80,rotation:-12},
  {kind:"party",top:"9%",left:"71%",size:88,rotation:16},
  {kind:"soju",top:"18%",left:"1%",size:84,rotation:-16},
  {kind:"star",top:"29%",left:"75%",size:86,rotation:11},
  {kind:"balloon",top:"40%",left:"4%",size:80,rotation:7},
  {kind:"confetti",top:"48%",left:"69%",size:74,rotation:-10},
  {kind:"smile",top:"58%",left:"68%",size:81,rotation:-15},
  {kind:"soju",top:"67%",left:"2%",size:83,rotation:12},
  {kind:"party",top:"75%",left:"73%",size:77,rotation:-13},
  {kind:"star",top:"83%",left:"9%",size:82,rotation:17},
  {kind:"balloon",top:"92%",left:"66%",size:78,rotation:-14},
  {kind:"confetti",top:"97%",left:"40%",size:70,rotation:11},
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
