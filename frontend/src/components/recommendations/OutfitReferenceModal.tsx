import { useEffect, useState } from 'react';
import { ExternalLink, ImageOff, RotateCcw } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogOverlay, DialogTitle } from '@/components/ui/dialog';
import closeIcon from '@/assets/icons/ic_X.svg';
import hangerIcon from '@/assets/icons/il_hanger.svg';
import { getOutfitReferences } from '@/lib/api/recommendations';
import type { OutfitReferenceDto, OutfitReferencesDto, SkyTag, StyleTag, TempBand } from '@/lib/api/types';

interface OutfitReferenceModalProps {
  open: boolean;
  onClose: () => void;
  weatherId?: string;
}

const STYLE_LABELS: Record<StyleTag, string> = {
  MINIMAL: '미니멀',
  STREET: '스트릿',
  CASUAL: '캐주얼',
  CLASSIC: '클래식',
  FORMAL: '포멀',
  SPORTY: '스포티',
};

const STYLES = Object.keys(STYLE_LABELS) as StyleTag[];

const TEMP_BAND_LABELS: Record<TempBand, string> = {
  T28UP: '28°C 이상',
  T23_27: '23~27°C',
  T20_22: '20~22°C',
  T17_19: '17~19°C',
  T12_16: '12~16°C',
  T9_11: '9~11°C',
  T5_8: '5~8°C',
  T4DOWN: '4°C 이하',
};

const SKY_LABELS: Record<SkyTag, string> = {
  CLEAR: '맑음',
  CLOUDY: '흐림',
  RAIN: '비',
  SNOW: '눈',
};

const LIMIT = 12;

// 모달이 화면 폭을 따라 줄어들기 때문에 열 수도 같이 줄인다. 4열 고정이면 좁은 화면에서 사진이 손톱만 해진다.
const GRID_COLUMNS = 'grid-cols-2 sm:grid-cols-3 md:grid-cols-4';

/**
 * 오늘 날씨에 맞는 코디 참고 사진. 팀이 Pinterest 에 큐레이션한 핀을 보여준다.
 *
 * 사진은 복사하지 않고 Pinterest 주소를 그대로 쓰므로, 사진마다 원본 핀으로 연결해야 한다.
 */
export default function OutfitReferenceModal({ open, onClose, weatherId }: OutfitReferenceModalProps) {
  const [selectedStyles, setSelectedStyles] = useState<StyleTag[]>([]);
  const [result, setResult] = useState<OutfitReferencesDto | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [retryKey, setRetryKey] = useState(0);

  useEffect(() => {
    if (!open || !weatherId) return;

    // 스타일을 빠르게 바꾸면 응답이 순서대로 오지 않는다. 늦게 온 이전 응답이 화면을 덮지 않게 한다.
    let ignore = false;
    setLoading(true);
    setError(null);

    getOutfitReferences({ weatherId, styles: selectedStyles, limit: LIMIT })
      .then((response) => {
        if (!ignore) setResult(response);
      })
      .catch(() => {
        if (!ignore) setError('코디 사진을 불러오지 못했어요.');
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });

    return () => {
      ignore = true;
    };
  }, [open, weatherId, selectedStyles, retryKey]);

  const toggleStyle = (style: StyleTag) => {
    setSelectedStyles((current) =>
      current.includes(style) ? current.filter((s) => s !== style) : [...current, style]
    );
  };

  const references = result?.references ?? [];
  const condition = result
    ? `${TEMP_BAND_LABELS[result.tempBand]} · ${SKY_LABELS[result.sky]} 기준`
    : '오늘 날씨 기준';

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogOverlay className="bg-[rgba(19,19,22,0.5)]" />
      <DialogContent
        // DialogContent 기본값 sm:max-w-lg(512px) 를 덮어써야 폭이 넓어진다
        className="bg-white box-border flex flex-col gap-5 overflow-hidden p-5 sm:p-[30px] rounded-[30px] w-[860px] max-w-[calc(100vw-2rem)] sm:max-w-[calc(100vw-2rem)] max-h-[85vh]"
        showCloseButton={false}
      >
        {/* 헤더 */}
        <div className="flex items-start justify-between w-full shrink-0">
          <div className="flex flex-col gap-1">
            <DialogTitle className="font-bold text-[#212126] text-[22px] tracking-[-0.55px] leading-normal">
              오늘의 코디 참고
            </DialogTitle>
            <DialogDescription className="font-semibold text-[#808089] text-[16px] tracking-[-0.4px] leading-normal">
              {condition}
            </DialogDescription>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="size-[30px] flex items-center justify-center hover:bg-gray-100 rounded transition-colors shrink-0"
          >
            <img src={closeIcon} alt="닫기" className="size-6" />
          </button>
        </div>

        {/* 스타일 필터 */}
        <div className="flex flex-wrap items-center gap-2 shrink-0" role="group" aria-label="스타일 필터">
          <StyleChip
            label="전체"
            active={selectedStyles.length === 0}
            onClick={() => setSelectedStyles([])}
          />
          {STYLES.map((style) => (
            <StyleChip
              key={style}
              label={STYLE_LABELS[style]}
              active={selectedStyles.includes(style)}
              onClick={() => toggleStyle(style)}
            />
          ))}
        </div>

        {/* 본문 */}
        <div className="min-h-[320px] overflow-y-auto -mx-1 px-1">
          {error ? (
            <div className="flex flex-col items-center justify-center gap-3 min-h-[320px]">
              <p className="font-semibold text-[#696975] text-[16px]" role="alert">{error}</p>
              <button
                type="button"
                onClick={() => setRetryKey((key) => key + 1)}
                className="flex items-center gap-1.5 h-[40px] px-4 rounded-[12px] border border-[#d4d4d9] font-semibold text-[#696975] text-[15px] hover:bg-gray-50 transition-colors"
              >
                다시 시도
                <RotateCcw className="size-4" aria-hidden="true" />
              </button>
            </div>
          ) : loading && references.length === 0 ? (
            <div className={`grid ${GRID_COLUMNS} gap-4`} aria-busy="true" aria-label="불러오는 중">
              {Array.from({ length: 8 }).map((_, index) => (
                <div key={index} className="aspect-[3/4] rounded-[16px] bg-gray-100 animate-pulse" />
              ))}
            </div>
          ) : references.length === 0 ? (
            <EmptyReferences filtered={selectedStyles.length > 0} />
          ) : (
            <div className={`grid ${GRID_COLUMNS} gap-4 transition-opacity ${loading ? 'opacity-50' : ''}`}>
              {references.map((reference) => (
                <ReferenceCard key={reference.pinId} reference={reference} />
              ))}
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}

function StyleChip({ label, active, onClick }: { label: string; active: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={`rounded-full border px-3.5 py-1.5 text-sm font-semibold transition-colors ${
        active
          ? 'border-[#1e89f4] bg-[#1e89f4] text-white'
          : 'border-[#e1e1e5] bg-white text-[#696975] hover:border-blue-300 hover:bg-blue-50 hover:text-blue-600'
      }`}
    >
      {label}
    </button>
  );
}

function ReferenceCard({ reference }: { reference: OutfitReferenceDto }) {
  const [broken, setBroken] = useState(false);

  return (
    <a
      href={reference.pinUrl}
      target="_blank"
      rel="noopener noreferrer"
      className="group flex flex-col gap-2"
      aria-label={`${reference.title ?? '코디 사진'} — Pinterest 에서 보기`}
    >
      <div className="relative aspect-[3/4] overflow-hidden rounded-[16px] bg-gray-100">
        {broken ? (
          <div className="flex h-full flex-col items-center justify-center gap-2 text-[#a9a9b1]">
            <ImageOff className="size-6" aria-hidden="true" />
            <span className="text-xs font-semibold">이미지를 불러올 수 없어요</span>
          </div>
        ) : (
          <img
            src={reference.imageUrl}
            alt={reference.title ?? '코디 사진'}
            loading="lazy"
            onError={() => setBroken(true)}
            className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-[1.03]"
          />
        )}
        <span className="absolute bottom-2 right-2 flex items-center gap-1 rounded-full bg-black/55 px-2 py-1 text-[11px] font-semibold text-white opacity-0 transition-opacity group-hover:opacity-100">
          Pinterest
          <ExternalLink className="size-3" aria-hidden="true" />
        </span>
      </div>
      {reference.title && (
        <p className="truncate text-[14px] font-semibold text-[#212126] tracking-[-0.35px]">{reference.title}</p>
      )}
      {reference.styles.length > 0 && (
        <div className="flex flex-wrap gap-1">
          {reference.styles.map((style) => (
            <span key={style} className="rounded-full bg-[#f2f2f3] px-2 py-0.5 text-[12px] font-semibold text-[#696975]">
              {STYLE_LABELS[style]}
            </span>
          ))}
        </div>
      )}
    </a>
  );
}

function EmptyReferences({ filtered }: { filtered: boolean }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 min-h-[320px] text-center">
      <img src={hangerIcon} alt="" className="size-[52px] opacity-60" />
      <p className="font-bold text-[20px] text-gray-400 tracking-[-0.5px]">
        {filtered ? '선택한 스타일의 코디가 아직 없어요' : '오늘 날씨에 맞는 코디 사진을 준비 중이에요'}
      </p>
      <p className="font-semibold text-[15px] text-[#a9a9b1]">
        {filtered ? '다른 스타일을 골라보세요.' : '곧 날씨별 코디 참고 사진을 볼 수 있어요.'}
      </p>
    </div>
  );
}
