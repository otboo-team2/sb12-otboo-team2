import { useNavigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import fittingArrow from '@/assets/guide/fitting-arrow.png';
import modelSelection from '@/assets/guide/model-selection.png';
import fittingComplete from '@/assets/guide/fitting-complete.png';
import knit from '@/assets/guide/knit.png';
import shirt from '@/assets/guide/shirt.png';
import pants from '@/assets/guide/pants.png';
import hoodie from '@/assets/guide/hoodie.png';
import shoes from '@/assets/guide/shoes.png';
import cap from '@/assets/guide/cap.png';
import { Info } from 'lucide-react';

export default function VirtualFittingGuidePage() {
  const navigate = useNavigate();

  return (
    <div className="h-full overflow-hidden bg-white px-8 py-6">
      <section className="mx-auto flex w-full max-w-[1280px] flex-col items-center text-center">
        <span className="rounded-full bg-[#EEF6F1] px-5 py-2 text-sm font-bold text-[#527565]">GUIDE</span>
        <h1 className="mt-3 text-5xl font-extrabold tracking-tight text-gray-900">가상 <span className="text-blue-500">피팅</span></h1>
        <p className="mt-3 text-xl font-semibold text-gray-800">내 옷장의 옷, 입어보기 전에 미리 확인해보세요.</p>
        <p className="mt-2 text-lg text-gray-500">모델을 선택하고 원하는 옷을 고르면 AI가 가상 피팅 이미지를 만들어드려요.</p>

        <div className="mt-7 flex w-full items-center justify-center gap-3">
          <GuideCard step="STEP 01" title="모델 선택" description={<>기본 모델을 사용하거나<br />내 전신 사진을 등록해 주세요.</>}>
            <img src={modelSelection} alt="모델 선택" className="h-56 w-full object-contain" />
          </GuideCard>
          <img src={fittingArrow} alt="다음 단계" className="hidden h-12 w-12 object-contain lg:block" />
          <GuideCard step="STEP 02" title="의상 선택" description={<>내 옷장에서 상의와 하의를 선택해 주세요.<br />추가 의상은 선택할 수 있어요.</>}>
            <div className="grid h-64 grid-cols-3 grid-rows-2 gap-2 p-2">
              {[['니트', knit], ['바지', pants], ['모자', cap], ['셔츠', shirt], ['후드티', hoodie], ['신발', shoes]].map(([label, image], index) => (
                <div key={label} className={`relative flex items-center justify-center rounded-xl border-2 bg-white p-1 ${index === 0 ? 'border-blue-400 bg-blue-50 shadow-sm' : 'border-gray-100'}`}>
                  <img src={image} alt={label} className="h-full w-full object-contain" />
                  {index === 0 && <span className="absolute -right-1 -top-1 flex size-5 items-center justify-center rounded-full bg-blue-500 text-xs font-bold text-white">✓</span>}
                </div>
              ))}
            </div>
          </GuideCard>
          <img src={fittingArrow} alt="다음 단계" className="hidden h-12 w-12 object-contain lg:block" />
          <GuideCard step="STEP 03" title="가상 피팅 생성" description={<>선택한 모델과 의상을 바탕으로<br />AI가 가상 피팅 이미지를 생성해요.</>}>
            <img src={fittingComplete} alt="가상 피팅 생성 결과" className="h-60 w-full object-contain" />
          </GuideCard>
        </div>

        <div className="mt-8 flex items-center rounded-2xl border border-slate-200 bg-slate-100 px-8 py-3 text-sm font-semibold text-slate-700">
          <Info className="size-5 shrink-0 text-slate-600" />
          <span className="ml-3 border-l border-slate-300 pl-4">전신이 잘 보이는 정면 사진을 사용하면 더 자연스러운 결과를 얻을 수 있어요.</span>
        </div>
        <button
          type="button"
          onClick={() => navigate('/virtual-fitting/start')}
          className="mt-8 h-[60px] min-w-[360px] rounded-full bg-blue-500 px-10 text-xl font-bold text-white shadow-sm transition-colors hover:bg-blue-600"
        >
          가상 피팅 시작하기&nbsp; →
        </button>
      </section>
    </div>
  );
}

function GuideCard({step, title, description, children}: {step: string; title: string; description: ReactNode; children: ReactNode}) {
  return <article className="flex min-h-[450px] flex-1 flex-col rounded-[24px] border border-gray-200 bg-white px-6 py-6 shadow-[0_4px_20px_rgba(55,55,64,0.04)]">
    <span className="mx-auto rounded-full bg-[#EEF6F1] px-4 py-1.5 text-sm font-bold text-[#527565]">{step}</span>
    <h2 className="mt-4 text-2xl font-extrabold text-gray-900">{title}</h2>
    <p className="mt-3 min-h-[52px] text-base leading-6 text-gray-500">{description}</p>
    <div className="mt-3 flex flex-1 items-center justify-center">{children}</div>
  </article>;
}
