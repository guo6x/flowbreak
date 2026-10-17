import logo from '../assets/icon.svg';

export function BrandMark({ className = '' }: { className?: string }) {
  return <img src={logo} alt="" aria-hidden="true" width="44" height="44" className={`brand-mark ${className}`} />;
}

export default function Brand({ compact = false, className = '' }: { compact?: boolean; className?: string }) {
  return (
    <div className={`inline-flex items-center gap-3 ${className}`}>
      <BrandMark />
      <div className="text-left">
        <p className="brand-wordmark">FlowBreak</p>
        {!compact && <p className="text-[12px] text-gray-500 mt-0.5">给注意力，留一点空白</p>}
      </div>
    </div>
  );
}
