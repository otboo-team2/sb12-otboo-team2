export type Role = 'USER' | 'ADMIN';
export type OAuthProvider = 'google' | 'kakao';
export type Gender = 'MALE' | 'FEMALE' | 'OTHER';
export type SortDirection = 'ASCENDING' | 'DESCENDING';
export type ClothesType =
  | 'TOP'
  | 'BOTTOM'
  | 'DRESS'
  | 'OUTER'
  | 'UNDERWEAR'
  | 'ACCESSORY'
  | 'SHOES'
  | 'SOCKS'
  | 'HAT'
  | 'BAG'
  | 'SCARF'
  | 'ETC';
export type SkyStatus = 'CLEAR' | 'MOSTLY_CLOUDY' | 'CLOUDY';
export type PrecipitationType = 'NONE' | 'RAIN' | 'RAIN_SNOW' | 'SNOW' | 'SHOWER';
export type WindStrength = 'WEAK' | 'MODERATE' | 'STRONG';
export type NotificationType =
    | 'ROLE_CHANGED'
    | 'CLOTHES_ATTRIBUTE_ADDED'
    | 'FEED_LIKED'
    | 'FEED_COMMENTED'
    | 'FOLLOW_CREATED'
    | 'FEED_CREATED'
    | 'DM_RECEIVED'
    | 'VIRTUAL_TRY_ON_COMPLETED';
export type NotificationLevel = 'INFO' | 'WARNING' | 'ERROR';
export type VirtualTryOnJobStatus = 'PENDING' | 'PROCESSING' | 'SUCCEEDED' | 'FAILED';

export interface ErrorResponse {
  exceptionName: string;
  message: string;
  details?: Record<string, string>;
}

export interface UserDto {
  id: string;
  createdAt: string;
  email: string;
  name: string;
  role: Role;
  linkedOAuthProviders: OAuthProvider[];
  locked: boolean;
}

export interface UserSummary {
  userId: string;
  name: string;
  profileImageUrl?: string;
}

export interface AuthorDto {
  userId: string;
  name: string;
  profileImageUrl?: string;
}

export interface ProfileDto {
  userId: string;
  name: string;
  gender?: Gender;
  birthDate?: string;
  location?: WeatherAPILocation;
  temperatureSensitivity?: number;
  profileImageUrl?: string;
}

export interface WeatherAPILocation {
  latitude: number;
  longitude: number;
  x: number;
  y: number;
  locationNames: string[];
}

export interface TemperatureDto {
  current: number;
  comparedToDayBefore: number;
  min: number;
  max: number;
}

export interface HumidityDto {
  current: number;
  comparedToDayBefore: number;
}

export interface PrecipitationDto {
  type: PrecipitationType;
  amount: number;
  probability: number;
}

export interface WindSpeedDto {
  speed: number;
  asWord: WindStrength;
}

export interface WeatherDto {
  id: string;
  forecastedAt: string;
  forecastAt: string;
  location: WeatherAPILocation;
  skyStatus: SkyStatus;
  precipitation: PrecipitationDto;
  humidity: HumidityDto;
  temperature: TemperatureDto;
  windSpeed: WindSpeedDto;
}

export interface WeatherSummaryDto {
  weatherId: string;
  skyStatus: SkyStatus;
  precipitation: PrecipitationDto;
  temperature: TemperatureDto;
}

export interface ClothesAttributeDto {
  definitionId: string;
  value: string;
}

export interface ClothesAttributeWithDefDto {
  definitionId: string;
  definitionName: string;
  selectableValues: string[];
  value: string;
}

export interface ClothesAttributeDefDto {
  id: string;
  createdAt: string;
  name: string;
  selectableValues: string[];
  selectableValueIds?: string[];
}

export interface ClothesDto {
  id: string;
  ownerId: string;
  name: string;
  imageUrl?: string;
  type: ClothesType;
  attributes: ClothesAttributeWithDefDto[];
}

export type ClothesExtractionSource =
  | 'STRUCTURED_DATA'
  | 'PAGE_TEXT'
  | 'DETAIL_IMAGE';

export interface ExtractedClothesAttributeDto {
  definitionId: string;
  definitionName: string;
  value: string;
  evidence: string;
  source: ClothesExtractionSource;
}

export interface ClothesExtractionFailureDto {
  field: string;
  reason: string;
}

export interface ClothesExtractionDto {
  name?: string;
  type?: ClothesType;
  attributes: ExtractedClothesAttributeDto[];
  imageUrl?: string;
  failures: ClothesExtractionFailureDto[];
}

export interface OotdDto {
  clothesId: string;
  name: string;
  imageUrl?: string;
  type: ClothesType;
  attributes: ClothesAttributeWithDefDto[];
}

export interface FeedDto {
  id: string;
  createdAt: string;
  updatedAt: string;
  author: AuthorDto;
  weather: WeatherSummaryDto;
  ootds: OotdDto[];
  content: string;
  likeCount: number;
  commentCount: number;
  likedByMe: boolean;
}

export interface CommentDto {
  id: string;
  createdAt: string;
  feedId: string;
  author: AuthorDto;
  content: string;
}

export interface FollowDto {
  id: string;
  followee: UserSummary;
  follower: UserSummary;
}

export interface FollowSummaryDto {
  followeeId: string;
  followerCount: number;
  followingCount: number;
  followedByMe: boolean;
  followedByMeId?: string;
  followingMe: boolean;
}

export interface RecommendationDto {
  weatherId: string;
  userId: string;
  clothes: OotdDto[];
}

// Pinterest 코디 참고 사진. 서버의 pinterest.tag enum 이름과 같아야 한다.
export type StyleTag = 'MINIMAL' | 'STREET' | 'CASUAL' | 'CLASSIC' | 'FORMAL' | 'SPORTY';
export type TempBand = 'T28UP' | 'T23_27' | 'T20_22' | 'T17_19' | 'T12_16' | 'T9_11' | 'T5_8' | 'T4DOWN';
export type SkyTag = 'CLEAR' | 'CLOUDY' | 'RAIN' | 'SNOW';

export interface OutfitReferenceDto {
  pinId: string;
  imageUrl: string;
  /** 원본 핀 주소. 사진마다 반드시 이 주소로 연결한다 */
  pinUrl: string;
  link: string | null;
  title: string | null;
  styles: StyleTag[];
}

export interface OutfitReferencesDto {
  weatherId: string;
  /** 이번 검색에 쓴 조건. 실제 검색은 앞뒤 기온 구간까지 넓혀서 한다 */
  tempBand: TempBand;
  sky: SkyTag;
  /** Pinterest 동기화 전이거나 조건에 맞는 핀이 없으면 비어 있다 */
  references: OutfitReferenceDto[];
}

export interface NotificationDto {
  id: string;
  createdAt: string;
  receiverId: string;
  actorId: string | null;
  title: string;
  content: string;
  level: NotificationLevel;
  type: NotificationType;
  relatedEntityId: string | null;
}

export interface DirectMessageDto {
  id: string;
  createdAt: string;
  sender: UserSummary;
  receiver: UserSummary;
  content: string;
}

export interface DmConversationDto {
    messageId: string;
    lastMessageAt: string;
    lastMessageContent: string;
    partner: UserSummary;
}

export interface VirtualTryOnJobDto {
    jobId: string;
    status: VirtualTryOnJobStatus;
    resultImageUrl: string | null;
    failureReason: string | null;
    retryable: boolean;
}

export interface JwtDto {
  userDto: UserDto;
  accessToken: string;
}

export interface CursorResponse<T> {
  data: T[];
  nextCursor?: string;
  nextIdAfter?: string;
  hasNext: boolean;
  totalCount: number;
  sortBy: string;
  sortDirection: SortDirection;
}

export interface UserCreateRequest {
  name: string;
  email: string;
  password: string;
}

export interface SignInRequest {
  username: string;
  password: string;
}

export interface ResetPasswordRequest {
  email: string;
}

export interface ChangePasswordRequest {
  password: string;
}

export interface UserRoleUpdateRequest {
  role: Role;
}

export interface UserLockUpdateRequest {
  locked: boolean;
}

export interface ProfileUpdateRequest {
  name?: string;
  gender?: Gender;
  birthDate?: string;
  location?: WeatherAPILocation;
  temperatureSensitivity?: number;
}

export interface FeedCreateRequest {
  authorId: string;
  weatherId: string;
  clothesIds: string[];
  content: string;
}

export interface FeedUpdateRequest {
  content: string;
}

export interface CommentCreateRequest {
  feedId: string;
  authorId: string;
  content: string;
}

export interface ClothesCreateRequest {
  ownerId: string;
  name: string;
  type: ClothesType;
  attributes: ClothesAttributeDto[];
  sourceImageUrl?: string;
}

export interface ClothesUpdateRequest {
  name?: string;
  type?: ClothesType;
  attributes?: ClothesAttributeDto[];
}

export interface ClothesAttributeDefCreateRequest {
  name: string;
  selectableValues: string[];
}

export interface ClothesAttributeDefUpdateRequest {
  name?: string;
  selectableValues?: string[];
}

export interface FollowCreateRequest {
  followeeId: string;
  followerId: string;
}

export interface VirtualTryOnRequest {
    topClothesId: string;
    bottomClothesId: string;
    additionalClothesId?: string | null;
}

export interface CursorParams {
  cursor?: string;
  idAfter?: string;
  limit: number;
}

export interface SortParams {
  sortDirection: SortDirection;
}

export interface UserListParams extends CursorParams, SortParams {
  emailLike?: string;
  roleEqual?: Role;
  locked?: boolean;
  sortBy: 'email' | 'createdAt';
}

export interface FeedListParams extends CursorParams, SortParams {
  keywordLike?: string;
  skyStatusEqual?: SkyStatus;
  precipitationTypeEqual?: PrecipitationType;
  authorIdEqual?: string;
  sortBy: 'createdAt' | 'likeCount';
}

export interface ClothesListParams extends CursorParams {
  typeEqual?: ClothesType;
  ownerId: string;
}

export interface ClothesAttributeDefListParams extends CursorParams, SortParams {
  sortBy: "createdAt" | "name";
  keywordLike?: string;
}

export interface FollowingListParam extends CursorParams {
  followerId: string;
  nameLike?: string;
}

export interface FollowerListParam extends CursorParams {
  followeeId: string;
  nameLike?: string;
}

export interface FollowListResponse extends CursorResponse<FollowDto> {}

export interface WeatherParams {
  longitude: number;
  latitude: number;
}

export interface RecommendationParams {
  weatherId: string;
}

export interface OutfitReferenceParams {
  weatherId: string;
  styles?: StyleTag[];
  limit?: number;
}

export interface DirectMessageParams extends CursorParams {
  userId: string;
}

export interface FeedCommentParams extends CursorParams {
  feedId: string;
}
